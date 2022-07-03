package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.RabbitMQConfig;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.CouponStateEnum;
import net.xdclass.enums.ProductOrderStateEnum;
import net.xdclass.enums.StockTaskStateEnum;
import net.xdclass.exception.BizException;
import net.xdclass.fegin.ProductOrderFeginService;
import net.xdclass.mapper.ProductMapper;
import net.xdclass.mapper.ProductTaskMapper;
import net.xdclass.model.ProductDO;
import net.xdclass.model.ProductMessage;
import net.xdclass.model.ProductTaskDO;
import net.xdclass.request.LockProductRequest;
import net.xdclass.request.OrderItemRequest;
import net.xdclass.service.ProductService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.ProductVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductTaskMapper productTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConfig rabbitMQConfig;
    
    @Autowired
    private ProductOrderFeginService productOrderFeginService;

    /**
     * 分页查询商品列表
     * @param page
     * @param size
     * @return
     */
    @Override
    public Map<String, Object> page(int page, int size) {
        Page<ProductDO> pageInfo = new Page<>(page,size);
        Page<ProductDO> productDOPage = productMapper.selectPage(pageInfo, new QueryWrapper<ProductDO>()
                .orderByDesc("create_time"));

        Map<String,Object> pageMap = new HashMap<>(3);
        pageMap.put("total_record",productDOPage.getTotal());
        pageMap.put("total_page",productDOPage.getPages());
        pageMap.put("current_data",productDOPage.getRecords().stream().map(obj->beanProcess(obj)).collect(Collectors.toList()));
        return pageMap;
    }

    /**
     * 根据id查询商品详情
     * @param productId
     * @return
     */
    @Override
    public ProductVO findDetailById(long productId) {
        ProductDO productDO = productMapper.selectOne(new QueryWrapper<ProductDO>().eq("id", productId));
        return beanProcess(productDO);
    }

    /**
     * 批量查询商品最新价格
     * @param productIdList
     * @return
     */
    @Override
    public List<ProductVO> findProductsByIdBatch(List<Long> productIdList) {

        List<ProductDO> productDOList = productMapper.selectList(new QueryWrapper<ProductDO>()
                .in("id", productIdList));
        List<ProductVO> productVOList = productDOList.stream().map(obj -> beanProcess(obj)).collect(Collectors.toList());
        return productVOList;
    }

    /**
     * 锁定商品库存
     * 1)遍历商品，锁定每个商品购买数量
     * 2)每一次锁定的时候，都要发送延迟消息
     * @param lockProductRequest
     * @return
     */
    @Override
    public JsonData lockProductStock(LockProductRequest lockProductRequest) {
        String orderOutTradeNo = lockProductRequest.getOrderOutTradeNo();
        List<OrderItemRequest> orderItemList = lockProductRequest.getOrderItemList();
        //一行代码提取对象里面的id，并加入到集合中
        List<Long> orderItemProductIdList = orderItemList.stream()
                .map(OrderItemRequest::getProductId)
                .collect(Collectors.toList());
        //批量查询
        List<ProductVO> productsByIdBatch = this.findProductsByIdBatch(orderItemProductIdList);
        //分组
        Map<Long, ProductVO> productMap = productsByIdBatch.stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity()));
        for (OrderItemRequest item : orderItemList) {
            //锁定商品记录
            int rows = productMapper.lockProductStock(item.getProductId(),item.getBuyNum());
            if (rows != 1){
                throw new BizException(BizCodeEnum.ORDER_CONFIRM_LOCK_PRODUCT_FAIL);
            }else {
                //插入商品的product_task
                ProductTaskDO productTaskDO = new ProductTaskDO();
                ProductVO productVO = productMap.get(item.getProductId());
                productTaskDO.setBuyNum(item.getBuyNum());
                productTaskDO.setLockState(StockTaskStateEnum.LOCK.name());
                productTaskDO.setProductId(item.getProductId());
                productTaskDO.setProductName(productVO.getTitle());
                productTaskDO.setOutTradeNo(orderOutTradeNo);
                productTaskMapper.insert(productTaskDO);
                log.info("商品库存锁定-插入商品product_task成功:{}",productTaskDO);

                //发送MQ延迟消息，解锁商品库存
                ProductMessage productMessage = new ProductMessage();
                productMessage.setOutTradeNo(orderOutTradeNo);
                productMessage.setTaskId(productTaskDO.getId());

                rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(),rabbitMQConfig.getStockReleaseDelayRoutingKey(),productMessage);
                log.info("商品库存锁定延迟消息发送成功:{}",productMessage);
            }
        }

        return JsonData.buildSuccess();
    }

    /**
     * 释放商品库存
     * @param productMessage
     * @return
     */
    @Override
    @Transactional(rollbackFor=Exception.class,propagation= Propagation.REQUIRED)
    public boolean releaseProductStock(ProductMessage productMessage) {
        String outTradeNo = productMessage.getOutTradeNo();
        //查询工作单状态
        ProductTaskDO productTaskDO = productTaskMapper.selectOne(new QueryWrapper<ProductTaskDO>()
                .eq("id", productMessage.getTaskId()));
        if (productTaskDO == null){
            log.warn("工作单不存在，消息体为:{}",productMessage);
            return true;
        }

        //lock状态才处理
        if (productTaskDO.getLockState().equalsIgnoreCase(StockTaskStateEnum.LOCK.name())){
            JsonData jsonData = productOrderFeginService.queryProductOrderState(outTradeNo);
            if (jsonData.getCode()==0){
                String state = jsonData.getData().toString();
                if (ProductOrderStateEnum.NEW.name().equalsIgnoreCase(state)){
                    //状态是NEW新建状态，则返回给消息队列重新投递
                    log.warn("订单状态为NEW,返回给消息队列重新投递:{}",productMessage);
                    return false;
                }
                //如果是已经支付
                if (ProductOrderStateEnum.PAY.name().equalsIgnoreCase(state)){
                    //如果是已经支付，修改task状态为finish
                    productTaskDO.setLockState(StockTaskStateEnum.FINISH.name());
                    productTaskMapper.update(productTaskDO,new QueryWrapper<ProductTaskDO>()
                            .eq("id",productTaskDO.getId()));
                    log.info("订单已经支付，修改库存锁定工作单FINISH状态:{}",productMessage);
                    return true;
                }
            }
            //订单不存在，或者订单被取消，确认消息修改task状态为CANCEL，恢复优惠券使用记录为NEW
            log.warn("订单不存在，或者订单被取消，确认消息修改task状态为CANCEL，恢复商品库存使用记录为NEW,message：{}",productMessage);
            productTaskDO.setLockState(StockTaskStateEnum.CANCEL.name());
            productTaskMapper.update(productTaskDO,new QueryWrapper<ProductTaskDO>()
                    .eq("id",productTaskDO.getId()));

            //恢复商品库存,即锁定库存的值减去当前购买的值
            productMapper.unLockProductStock(productTaskDO.getProductId(), productTaskDO.getBuyNum());
            return true;
        }else {
            //非lock状态
            log.warn("工作单状态不是lock：state:{},消息体:{}",productTaskDO.getLockState(),productMessage);
            return true;
        }
    }

    private ProductVO beanProcess(ProductDO productDO) {
        ProductVO productVO = new ProductVO();
        BeanUtils.copyProperties(productDO,productVO);
        productVO.setStock(productDO.getStock()-productDO.getLockStock());
        return productVO;
    }
}
