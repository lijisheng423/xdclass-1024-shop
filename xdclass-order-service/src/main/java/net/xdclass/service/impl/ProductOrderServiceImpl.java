package net.xdclass.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alipay.api.domain.PageInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.generator.config.IFileCreate;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.component.PayFactory;
import net.xdclass.config.RabbitMQConfig;
import net.xdclass.constant.TimeConstant;
import net.xdclass.enums.*;
import net.xdclass.exception.BizException;
import net.xdclass.fegin.CouponFeignService;
import net.xdclass.fegin.ProductFeignService;
import net.xdclass.fegin.UserFeignService;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.ProductOrderItemMapper;
import net.xdclass.mapper.ProductOrderMapper;
import net.xdclass.model.LoginUser;
import net.xdclass.model.OrderMessage;
import net.xdclass.model.ProductOrderDO;
import net.xdclass.model.ProductOrderItemDO;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.request.LockCouponRecordRequest;
import net.xdclass.request.LockProductRequest;
import net.xdclass.request.OrderItemRequest;
import net.xdclass.service.ProductOrderItemService;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Order;
import org.mockito.internal.util.StringUtil;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductOrderServiceImpl implements ProductOrderService {

    @Autowired
    private ProductOrderMapper productOrderMapper;

    @Autowired
    private UserFeignService userFeignService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private CouponFeignService couponFeignService;

    @Autowired
    private ProductOrderItemMapper productOrderItemMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    @Autowired
    private PayFactory payFactory;

    /**
     * 防重提交
     *
     * 用户微服务-确认收货地址
     *
     * 商品微服务-获取最新购物项和价格
     *
     * 订单验价
     *
     * 优惠券微服务-获取优惠券
     * 验证价格
     * 锁定优惠券
     *
     * 锁定商品库存
     *
     * 创建订单对象
     *
     * 创建子订单对象
     *
     * 发送延迟消息-用于自动关单
     *
     * 创建支付信息-对接三方支付
     * @param confirmOrderRequest
     * @return
     */
    @Override
    public JsonData confirmOrder(ConfirmOrderRequest confirmOrderRequest) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        String orderOutTradeNo = CommonUtil.getStringNumRandom(32);
        ProductOrderAddressVO productOrderAddressVO = this.getUserAddress(confirmOrderRequest.getAddressId());
        log.info("收货地址信息:{}",productOrderAddressVO);

        //获取用户加入购物车的商品
        List<Long> productIdList = confirmOrderRequest.getProductIdList();
        JsonData cartItemDate = productFeignService.confirmOrderCartItem(productIdList);
        List<OrderItemVO> orderItemVOList = cartItemDate.getData(new TypeReference<List<OrderItemVO>>(){});
        log.info("获取的商品:{}",orderItemVOList);
        if (null == orderItemVOList){
            //购物车商品不存在
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_CART_ITEM_NOT_EXIST);
        }
        
        //验证价格，减去商品优惠券
        this.checkPrice(orderItemVOList,confirmOrderRequest);

        //锁定优惠券
        this.lockCouponRecords(confirmOrderRequest,orderOutTradeNo);

        //锁定库存
        this.lockProductStocks(orderItemVOList,orderOutTradeNo);

        //创建订单
        ProductOrderDO productOrderDO = this.saveProductOrder(confirmOrderRequest, loginUser, orderOutTradeNo, productOrderAddressVO);

        //创建订单项
        this.saveProductOrderItems(orderOutTradeNo,productOrderDO.getId(),orderItemVOList);

        //发送延迟消息，用于自动关单
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOutTradeNo(orderOutTradeNo);
        rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(),rabbitMQConfig.getOrderCloseDelayRoutingKey(),orderMessage);

        //创建支付
        PayInfoVO payInfoVO = new PayInfoVO(productOrderDO.getOutTradeNo(),productOrderDO.getPayAmount(),productOrderDO.getPayType(),
                confirmOrderRequest.getClientType(),orderItemVOList.get(0).getProductTitle(),"", TimeConstant.ORDER_PAY_TIMEOUT_MILLS);
        String payResult = payFactory.pay(payInfoVO);
        if (StringUtils.isNotBlank(payResult)){
            log.info("创建支付订单成功:payInfoVO={}，payResult={}",payInfoVO,payResult);
            return JsonData.buildSuccess(payResult);
        }else {
            log.error("创建支付订单失败:payInfoVO={}，payResult={}",payInfoVO,payResult);
            return JsonData.buildResult(BizCodeEnum.PAY_ORDER_FAIL);
        }
    }

    /**
     * 新增订单项
     * @param orderOutTradeNo
     * @param orderId
     * @param orderItemVOList
     */
    private void saveProductOrderItems(String orderOutTradeNo, Long orderId, List<OrderItemVO> orderItemVOList) {

        List<ProductOrderItemDO> list = orderItemVOList.stream().map(
                obj -> {
                    ProductOrderItemDO itemDO = new ProductOrderItemDO();
                    itemDO.setBuyNum(obj.getBuyNum());
                    itemDO.setProductId(obj.getProductId());
                    itemDO.setProductImg(obj.getProductImg());
                    itemDO.setProductName(obj.getProductTitle());

                    itemDO.setOutTradeNo(orderOutTradeNo);
                    itemDO.setCreateTime(new Date());

                    //单价
                    itemDO.setAmount(obj.getAmount());
                    //总价
                    itemDO.setTotalAmount(obj.getTotalAmount());
                    itemDO.setProductOrderId(orderId);
                    return itemDO;
                }
        ).collect(Collectors.toList());

        productOrderItemMapper.insertBatch(list);
    }

    /**
     * 创建订单
     * @param confirmOrderRequest
     * @param loginUser
     * @param orderOutTradeNo
     * @param productOrderAddressVO
     */
    private ProductOrderDO saveProductOrder(ConfirmOrderRequest confirmOrderRequest, LoginUser loginUser, String orderOutTradeNo, ProductOrderAddressVO productOrderAddressVO) {
        ProductOrderDO productOrderDO = new ProductOrderDO();
        productOrderDO.setUserId(loginUser.getId());
        productOrderDO.setHeadImg(loginUser.getHeadImg());
        productOrderDO.setNickname(loginUser.getName());

        productOrderDO.setOutTradeNo(orderOutTradeNo);
        productOrderDO.setCreateTime(new Date());
        productOrderDO.setDel(0);
        productOrderDO.setOrderType(ProductOrderTypeEnum.DAILY.name());

        //实际支付的价格
        productOrderDO.setPayAmount(confirmOrderRequest.getRealPayAmount());

        //总价格，未使用优惠券的价格
        productOrderDO.setTotalAmount(confirmOrderRequest.getTotalAmount());
        productOrderDO.setState(ProductOrderStateEnum.NEW.name());
        productOrderDO.setPayType(ProductOrderPayTypeEnum.valueOf(confirmOrderRequest.getPayType()).name());

        productOrderDO.setReceiverAddress(JSON.toJSONString(productOrderAddressVO));

        productOrderMapper.insert(productOrderDO);
        return productOrderDO;




    }

    /**
     * 锁定商品库存
     * @param orderItemVOList
     * @param orderOutTradeNo
     */
    private void lockProductStocks(List<OrderItemVO> orderItemVOList, String orderOutTradeNo) {
        List<OrderItemRequest> orderItemList = orderItemVOList.stream().map(obj -> {
            OrderItemRequest orderItemRequest = new OrderItemRequest();
            orderItemRequest.setProductId(obj.getProductId());
            orderItemRequest.setBuyNum(obj.getBuyNum());
            return orderItemRequest;
        }).collect(Collectors.toList());

        LockProductRequest lockProductRequest = new LockProductRequest();
        lockProductRequest.setOrderOutTradeNo(orderOutTradeNo);
        lockProductRequest.setOrderItemList(orderItemList);

        JsonData jsonData = productFeignService.lockProductStock(lockProductRequest);
        if (jsonData.getCode()!=0){
            log.error("锁定商品库存失败:{}",lockProductRequest);
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_LOCK_PRODUCT_FAIL);
        }
    }

    /**
     * 锁定优惠券
     * @param confirmOrderRequest
     * @param orderOutTradeNo
     */
    private void lockCouponRecords(ConfirmOrderRequest confirmOrderRequest, String orderOutTradeNo) {
        List<Long> lockCouponRecordIds = new ArrayList<>();
        if (confirmOrderRequest.getCouponRecordId()>0){
            lockCouponRecordIds.add(confirmOrderRequest.getCouponRecordId());
            LockCouponRecordRequest lockCouponRecordRequest = new LockCouponRecordRequest();
            lockCouponRecordRequest.setLockCouponRecordIds(lockCouponRecordIds);
            lockCouponRecordRequest.setOrderOutTradeNo(orderOutTradeNo);
            //发起锁定优惠券请求
            JsonData jsonData = couponFeignService.lockCouponRecords(lockCouponRecordRequest);
            if (jsonData.getCode()!=0){
                throw new BizException(BizCodeEnum.COUPON_RECORD_LOCK_FAIL);
            }
        }
    }

    /**
     * 验证价格
     * 1)统计全部商品价格
     * 2)获取优惠券（判断是否满足优惠券的条件），总价-优惠券价格=最终的价格
     * @param orderItemVOList
     * @param confirmOrderRequest
     */
    private void checkPrice(List<OrderItemVO> orderItemVOList, ConfirmOrderRequest confirmOrderRequest) {
        //统计商品总价
        BigDecimal realPayAmount = new BigDecimal("0");
        if (null != orderItemVOList){
            for (OrderItemVO orderItemVO : orderItemVOList) {
                BigDecimal itemRealPayAmount = orderItemVO.getTotalAmount();
                realPayAmount = realPayAmount.add(itemRealPayAmount);
            }
        }

        //获取优惠券，判断是否可以使用
        CouponRecordVO couponRecordVO = getCartCouponRecord(confirmOrderRequest.getCouponRecordId());

        //计算购物车价格是否满足优惠券满减条件
        if (null != couponRecordVO){
            //计算是否满足满减
            if (realPayAmount.compareTo(couponRecordVO.getConditionPrice())<0){
                throw new BizException(BizCodeEnum.ORDER_CONFIRM_COUPON_FAIL);
            }
            if (couponRecordVO.getPrice().compareTo(realPayAmount)>0){
                realPayAmount = BigDecimal.ZERO;
            }else {
                realPayAmount = realPayAmount.subtract(couponRecordVO.getPrice());
            }
        }

        if (realPayAmount.compareTo(confirmOrderRequest.getRealPayAmount()) != 0){
            log.error("订单验价失败:{}",confirmOrderRequest);
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_PRICE_FAIL);
        }



    }

    /**
     * 获取优惠券
     * @param couponRecordId
     * @return
     */
    private CouponRecordVO getCartCouponRecord(Long couponRecordId) {
        if (null == couponRecordId || couponRecordId<0){
            return null;
        }
        JsonData couponData = couponFeignService.findUserCouponRecordById(couponRecordId);
        if (couponData.getCode()!=0){
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_COUPON_FAIL);
        }
        if (couponData.getCode()==0){
            CouponRecordVO couponRecordVO = couponData.getData(new TypeReference<CouponRecordVO>(){});
            if (!couponAvailable(couponRecordVO)){
                log.error("优惠券使用失败");
                throw new BizException(BizCodeEnum.COUPON_UNAVAILABLE);
            }
            return couponRecordVO;
        }
        return null;
    }

    /**
     * 判断优惠券是否可用
     * @param couponRecordVO
     * @return
     */
    private boolean couponAvailable(CouponRecordVO couponRecordVO) {
        if (couponRecordVO.getUseState().equalsIgnoreCase(CouponStateEnum.NEW.name())){
            long currentTimestamp = CommonUtil.getCurrentTimestamp();
            long end = couponRecordVO.getEndTime().getTime();
            long start = couponRecordVO.getStartTime().getTime();
            if (currentTimestamp>=start && currentTimestamp<=end){
                return true;
            }
        }
        
        return false;
        
    }

    /**
     * 获取收货地址详情
     * @param addressId
     * @return
     */
    private ProductOrderAddressVO getUserAddress(long addressId) {
        JsonData addressData = userFeignService.detail(addressId);
        if (addressData.getCode()!=0){
            log.error("获取收货地址失败，msg:{}",addressData);
            throw new BizException(BizCodeEnum.ADDRESS_NO_EXITS);
        }

        ProductOrderAddressVO productOrderAddressVO =  addressData.getData(new TypeReference<ProductOrderAddressVO>(){});
        return productOrderAddressVO;
    }

    /**
     * 查询订单状态
     * @param outTradeNo
     * @return
     */
    @Override
    public String queryProductOrderState(String outTradeNo) {
        ProductOrderDO productOrderDO = productOrderMapper.selectOne(new QueryWrapper<ProductOrderDO>()
                .eq("out_trade_no", outTradeNo));
        if (productOrderDO==null){
            return "";
        }else {
            return productOrderDO.getState();
        }
    }

    /**
     * 定时关单
     * @param orderMessage
     * @return
     */
    @Override
    public boolean closeProductOrder(OrderMessage orderMessage) {
        ProductOrderDO productOrderDO = productOrderMapper.selectOne(new QueryWrapper<ProductOrderDO>()
                .eq("out_trade_no", orderMessage.getOutTradeNo()));

        if (null == productOrderDO){
            //订单不存在
            log.warn("直接确认消息，订单不存在:{}",orderMessage);
            return true;
        }

        if (productOrderDO.getState().equalsIgnoreCase(ProductOrderStateEnum.PAY.name())){
            //已经支付
            log.info("直接确认消息，订单已经支付:{}",orderMessage);
            return true;
        }

        //向第三方支付查询订单是否真的支付
        PayInfoVO payInfoVO = new PayInfoVO();
        payInfoVO.setPayType(productOrderDO.getPayType());
        payInfoVO.setOutTradeNo(orderMessage.getOutTradeNo());
        String payResult = payFactory.queryPaySuccess(payInfoVO);

        //结果为空，则未支付成功，本地订单取消
        if (StringUtils.isBlank(payResult)){
            productOrderMapper.updateOrderPayState(productOrderDO.getOutTradeNo(),ProductOrderStateEnum.CANCEL.name(),
                    ProductOrderStateEnum.NEW.name());
            log.info("结果为空，则未支付成功，本地取消订单:{}",orderMessage);
            return true;
        }else {
            //支付成功，主动的将订单状态改为已经支付，造成该原因的情况可能是支付通道回调有问题
            log.warn("支付成功，主动的将订单状态改为已经支付，造成该原因的情况可能是支付通道回调有问题",orderMessage);
            productOrderMapper.updateOrderPayState(productOrderDO.getOutTradeNo(),ProductOrderStateEnum.PAY.name(),
                    ProductOrderStateEnum.NEW.name());
            return true;
        }
    }

    /**
     * 支付通知结果，更新订单状态
     * @param payType
     * @param paramsMap
     * @return
     */
    @Override
    public JsonData handlerOrderCallbackMsg(String payType, Map<String, String> paramsMap) {
        //如果消息过大，可以存放到MQ中慢慢消费
        if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.ALIPAY.name())){
            //支付宝支付
            //获取商户订单号
            String outTradeNo = paramsMap.get("out_trade_no");
            //交易状态
            String tradeStatus = paramsMap.get("trade_status");
            if ("TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISH".equalsIgnoreCase(tradeStatus)){
                //更新订单状态
                productOrderMapper.updateOrderPayState(outTradeNo,ProductOrderStateEnum.PAY.name(),ProductOrderStateEnum.NEW.name());
                return JsonData.buildSuccess();
            }
        }else if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.WECHAT.name())){
                //微信支付 todo
        }
        return JsonData.buildResult(BizCodeEnum.PAY_ORDER_CALLBACK_NOT_SUCCESS);
    }


    /**
     * 分页查询我的订单列表
     * @param page
     * @param size
     * @param state
     * @return
     */
    @Override
    public Map<String, Object> page(int page, int size, String state) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        Page<ProductOrderDO> pageInfo = new Page<>(page,size);
        Page<ProductOrderDO> orderDOPage = null;
        if (StringUtils.isBlank(state)){
            orderDOPage = productOrderMapper.selectPage(pageInfo, new QueryWrapper<ProductOrderDO>().eq("user_id", loginUser.getId()));
        }else {
            orderDOPage = productOrderMapper.selectPage(pageInfo, new QueryWrapper<ProductOrderDO>().eq("user_id", loginUser.getId()).eq("state",state));
        }
        List<ProductOrderDO> productOrderDOList = orderDOPage.getRecords();
        List<ProductOrderVO> productOrderVOList = productOrderDOList.stream().map(obj->{
            List<ProductOrderItemDO> productOrderItemDOList = productOrderItemMapper.selectList(new QueryWrapper<ProductOrderItemDO>().eq("out_trade_no", obj.getOutTradeNo()));
            List<OrderItemVO> orderItemVOList = productOrderItemDOList.stream().map(item -> {
                OrderItemVO orderItemVO = new OrderItemVO();
                BeanUtils.copyProperties(item, orderItemVO);
                return orderItemVO;
            }).collect(Collectors.toList());

            ProductOrderVO productOrderVO = new ProductOrderVO();
            BeanUtils.copyProperties(obj,productOrderVO);
            productOrderVO.setOrderItemVOList(orderItemVOList);
            return productOrderVO;
        }).collect(Collectors.toList());

        Map<String,Object> pageMap = new HashMap<>(3);
        pageMap.put("total_record",orderDOPage.getTotal());
        pageMap.put("total_page",orderDOPage.getPages());
        pageMap.put("current_data",productOrderVOList);
        return pageMap;
    }
}
