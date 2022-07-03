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
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.CouponRecordMapper;
import net.xdclass.mapper.CouponTaskMapper;
import net.xdclass.model.CouponRecordDO;
import net.xdclass.model.CouponRecordMessage;
import net.xdclass.model.CouponTaskDO;
import net.xdclass.model.LoginUser;
import net.xdclass.request.LockCouponRecordRequest;
import net.xdclass.service.CouponRecordService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CouponRecordVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponRecordServiceImpl implements CouponRecordService {

    @Autowired
    private CouponRecordMapper couponRecordMapper;

    @Autowired
    private CouponTaskMapper couponTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    @Autowired
    private ProductOrderFeginService productOrderFeginService;

    /**
     * 分页查询领券记录
     * @param page
     * @param size
     * @return
     */
    @Override
    public Map<String, Object> page(int page, int size) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        //封装分页信息
        Page<CouponRecordDO> pageInfo = new Page<>(page,size);
        Page<CouponRecordDO> recordDOPage = couponRecordMapper.selectPage(pageInfo, new QueryWrapper<CouponRecordDO>()
                .eq("user_id", loginUser.getId())
                .orderByDesc("create_time"));
        Map<String,Object> pageMap = new HashMap<>(3);
        pageMap.put("total_record",recordDOPage.getTotal());
        pageMap.put("total_page",recordDOPage.getPages());
        pageMap.put("current_data",recordDOPage.getRecords().stream().map(obj->beanProcess(obj)).collect(Collectors.toList()));
        return pageMap;
    }

    @Override
    public CouponRecordVO findById(long recordId) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        CouponRecordDO couponRecordDO = couponRecordMapper.selectOne(new QueryWrapper<CouponRecordDO>()
                .eq("id", recordId)
                .eq("user_id", loginUser.getId()));
        if (couponRecordDO==null){
            return null;
        }
        return beanProcess(couponRecordDO);
    }

    /**
     * 锁定优惠券
     * 1)锁定优惠券记录
     * 2)task表插入记录
     * 3)发送延迟消息
     * @param recordRequest
     * @return
     */
    @Override
    public JsonData lockCouponRecords(LockCouponRecordRequest recordRequest) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        String orderOutTradeNo = recordRequest.getOrderOutTradeNo();
        List<Long> lockCouponRecordIds = recordRequest.getLockCouponRecordIds();

        int updateRoes = couponRecordMapper.lockUseStateBatch(loginUser.getId(), CouponStateEnum.USED.name(),lockCouponRecordIds);

        List<CouponTaskDO> couponTaskDOList = lockCouponRecordIds.stream().map(obj -> {
            CouponTaskDO couponTaskDO = new CouponTaskDO();
            couponTaskDO.setCreateTime(new Date());
            couponTaskDO.setOutTradeNo(orderOutTradeNo);
            couponTaskDO.setCouponRecordId(obj);
            couponTaskDO.setLockState(StockTaskStateEnum.LOCK.name());
            return couponTaskDO;
        }).collect(Collectors.toList());

        int insertRows = couponTaskMapper.insertBatch(couponTaskDOList);
        log.info("优惠券记录锁定updateRoes={}",updateRoes);
        log.info("新增优惠券记录task insertRows={}",insertRows);

        if (lockCouponRecordIds.size() == insertRows && insertRows == updateRoes){
            //发送延迟消息
            for (CouponTaskDO couponTaskDO : couponTaskDOList) {
                CouponRecordMessage couponRecordMessage = new CouponRecordMessage();
                couponRecordMessage.setOutTradeNo(orderOutTradeNo);
                couponRecordMessage.setTaskId(couponTaskDO.getId());

                rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(),rabbitMQConfig.getCouponReleaseDelayRoutingKey(),couponRecordMessage);
                log.info("优惠券锁定消息发送成功:{}",couponRecordMessage.toString());
            }

            return JsonData.buildSuccess();
        }else {
            throw new BizException(BizCodeEnum.COUPON_RECORD_LOCK_FAIL);
        }
    }

    /**
     * 解锁优惠券记录
     * 1)查询task工作单是否存在
     * 2)查询订单状态
     * @param couponRecordMessage
     * @return
     */
    @Override
    @Transactional(rollbackFor=Exception.class,propagation= Propagation.REQUIRED)
    public boolean releaseCouponRecord(CouponRecordMessage couponRecordMessage) {
        //查询工作单是否存在
        CouponTaskDO couponTaskDO = couponTaskMapper.selectOne(new QueryWrapper<CouponTaskDO>()
                .eq("id", couponRecordMessage.getTaskId()));
        if (couponTaskDO==null){
            log.warn("工作单不存在,消息:{}",couponRecordMessage);
            return true;
        }

        //lock状态才进行处理
        if (couponTaskDO.getLockState().equalsIgnoreCase(StockTaskStateEnum.LOCK.name())){
            //查询订单状态
            JsonData jsonData = productOrderFeginService.queryProductOrderState(couponRecordMessage.getOutTradeNo());
            if (jsonData.getCode()==0){
                //正常响应，判断订单状态
                String state = jsonData.getData().toString();
                if (ProductOrderStateEnum.NEW.name().equalsIgnoreCase(state)){
                    //状态是NEW新建状态，则返回给消息队列重新投递
                    log.warn("订单状态为NEW,返回给消息队列重新投递:{}",couponRecordMessage);
                    return false;
                }
                //如果是已经支付
                if (ProductOrderStateEnum.PAY.name().equalsIgnoreCase(state)){
                    //如果是已经支付，修改task状态为finish
                    couponTaskDO.setLockState(StockTaskStateEnum.FINISH.name());
                    couponTaskMapper.update(couponTaskDO,new QueryWrapper<CouponTaskDO>()
                            .eq("id",couponTaskDO.getId()));
                    log.info("订单已经支付，修改库存锁定工作单FINISH状态:{}",couponRecordMessage);
                    return true;
                }
            }
            //订单不存在，或者订单被取消，确认消息修改task状态为CANCEL，恢复优惠券使用记录为NEW
            log.warn("订单不存在，或者订单被取消，确认消息修改task状态为CANCEL，恢复优惠券使用记录为NEW,message：{}",couponRecordMessage);
            couponTaskDO.setLockState(StockTaskStateEnum.CANCEL.name());
            couponTaskMapper.update(couponTaskDO,new QueryWrapper<CouponTaskDO>()
                    .eq("id",couponTaskDO.getId()));

            //恢复优惠券记录为NEW状态
            couponRecordMapper.updateState(couponTaskDO.getCouponRecordId(),CouponStateEnum.NEW.name());
            return true;
        }else {
            log.warn("工作单状态不是lock：state:{},消息体:{}",couponTaskDO.getLockState(),couponRecordMessage);
            return true;
        }
    }

    private CouponRecordVO beanProcess(CouponRecordDO couponRecordDO) {
        CouponRecordVO couponRecordVO = new CouponRecordVO();
        BeanUtils.copyProperties(couponRecordDO,couponRecordVO);
        return couponRecordVO;
    }
}
