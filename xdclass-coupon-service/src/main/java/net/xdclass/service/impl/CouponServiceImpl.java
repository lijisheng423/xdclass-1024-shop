package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.CouponCategoryEnum;
import net.xdclass.enums.CouponPublishEnum;
import net.xdclass.enums.CouponStateEnum;
import net.xdclass.exception.BizException;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.CouponMapper;
import net.xdclass.mapper.CouponRecordMapper;
import net.xdclass.model.CouponDO;
import net.xdclass.model.CouponRecordDO;
import net.xdclass.model.LoginUser;
import net.xdclass.request.NewUserCouponRequest;
import net.xdclass.service.CouponService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CouponVO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;
    
    @Autowired
    private CouponRecordMapper couponRecordMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Map<String, Object> pageCouponActivity(int page, int size) {
        Page<CouponDO> pageInfo = new Page<>(page,size);
        Page<CouponDO> couponDOPage = couponMapper.selectPage(pageInfo, new QueryWrapper<CouponDO>()
                .eq("publish", CouponPublishEnum.PUBLISH)
                .eq("category", CouponCategoryEnum.PROMOTION)
                .orderByDesc("create_time"));

        Map<String,Object> pageMap = new HashMap<>(3);
        pageMap.put("total_record",couponDOPage.getTotal());
        pageMap.put("total_page",couponDOPage.getPages());
        pageMap.put("current_data",couponDOPage.getRecords().stream().map(obj->beanProcess(obj)).collect(Collectors.toList()));
        return pageMap;
    }

    /**
     * 领取优惠券接口
     * 1.获取优惠券是否存在
     * 2.校验优惠券是否可以领取：时间，库存，超过限制
     * 3.扣减库存
     * 4.保存领券记录
     * @param couponId
     * @param categoryEnum
     * @return
     */
    @Transactional(rollbackFor=Exception.class,propagation= Propagation.REQUIRED)
    @Override
    public JsonData addCoupon(long couponId, CouponCategoryEnum categoryEnum) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        //锁粒度，锁用户防止超领
//        String lockKey = "lock:coupon:" + couponId+":"+loginUser.getId();
//        RLock rLock = redissonClient.getLock(lockKey);
//        //多个线程进入会阻塞等待释放锁，默认30s，有watch dog自动续期
//        rLock.lock();
        //加锁10s之后过期，没有watch dog功能
        //rLock.lock(10,TimeUnit.SECONDS);

        log.info("领券接口加锁成功:{}"+Thread.currentThread().getId());
        /*try {
            TimeUnit.SECONDS.sleep(90);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        //try {
            CouponDO couponDO = couponMapper.selectOne(new QueryWrapper<CouponDO>()
                    .eq("id", couponId)
                    .eq("category", categoryEnum.name()));
            if (couponDO == null) {
                throw new BizException(BizCodeEnum.COUPON_NO_EXITS);
            }
            //优惠券是否可以领取
            this.checkCoupon(couponDO, loginUser.getId());

            //构建领券记录
            CouponRecordDO couponRecordDO = new CouponRecordDO();
            BeanUtils.copyProperties(couponDO, couponRecordDO);
            couponRecordDO.setCreateTime(new Date());
            couponRecordDO.setUseState(CouponStateEnum.NEW.name());
            couponRecordDO.setUserId(loginUser.getId());
            couponRecordDO.setUserName(loginUser.getName());
            couponRecordDO.setCouponId(couponId);
            couponRecordDO.setId(null);

            //扣减库存
            int rows = couponMapper.reduceStock(couponId);

            //int flag = 1/0;

            if (rows == 1) {
                //扣减库存成功才保存记录
                couponRecordMapper.insert(couponRecordDO);
            } else {
                log.warn("发放优惠券失败：{}，用户：{}", couponDO, loginUser);
                throw new BizException(BizCodeEnum.COUPON_NO_STOCK);
            }
//        } finally {
//            rLock.unlock();
//            log.info("解锁成功:{}"+Thread.currentThread().getId());
//        }

        //布式锁放在事务内部，并不能解决超领问题，锁释放后，事务没有及时的提交，还是会超领
        return JsonData.buildSuccess();
    }

    /**
     * 新用户注册发送优惠券
     * 用户微服务调用的时候没传递token,因为没登录
     * 本地直接调用发放优惠券的方法，需要构造一个登录用户，存储在threadLocal
     * @param newUserCouponRequest
     * @return
     */
    @Override
    @Transactional(rollbackFor=Exception.class,propagation= Propagation.REQUIRED)
    public JsonData initNewUserCoupon(NewUserCouponRequest newUserCouponRequest) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(newUserCouponRequest.getUserId());
        loginUser.setName(newUserCouponRequest.getName());
        LoginInterceptor.threadLocal.set(loginUser);

        //查询新用户有哪些优惠券
        List<CouponDO> couponDOList = couponMapper.selectList(new QueryWrapper<CouponDO>()
                .eq("category", CouponCategoryEnum.NEW_USER.name()));
        for (CouponDO couponDO : couponDOList) {
            //幂等操作，调用需要加锁
            String lockKey = "lock:coupon:" + couponDO.getId()+":"+loginUser.getId();
            RLock rLock = redissonClient.getLock(lockKey);
            //多个线程进入会阻塞等待释放锁，默认30s，有watch dog自动续期
            rLock.lock();
            try {
                addCoupon(couponDO.getId(),CouponCategoryEnum.NEW_USER);
            }finally {
                rLock.unlock();
                log.info("解锁成功:{}"+Thread.currentThread().getId());
            }
        }
        //int b = 1/0;
        return JsonData.buildSuccess();
    }

    /**
     * 校验是否可以领取
     * @param couponDO
     * @param userId
     */
    private void checkCoupon(CouponDO couponDO, Long userId) {
        //库存是否足够
        if (couponDO.getStock()<=0){
            throw new BizException(BizCodeEnum.COUPON_NO_STOCK);
        }
        //判断优惠券是否是发布状态
        if (!couponDO.getPublish().equals(CouponPublishEnum.PUBLISH.name())){
            throw new BizException(BizCodeEnum.COUPON_GET_FAIL);
        }

        //是否在领取时间范围
        long time = CommonUtil.getCurrentTimestamp();
        long start = couponDO.getStartTime().getTime();
        long end = couponDO.getEndTime().getTime();
        if (time<start || time>end){
            throw new BizException(BizCodeEnum.COUPON_OUT_OF_TIME);
        }
        //用户是否超过限制
        Integer recordNum = couponRecordMapper.selectCount(new QueryWrapper<CouponRecordDO>()
                .eq("coupon_id", couponDO.getId())
                .eq("user_id", userId));
        if (recordNum >= couponDO.getUserLimit()){
            throw new BizException(BizCodeEnum.COUPON_OUT_OF_LIMIT);
        }
    }

    private Object beanProcess(CouponDO couponDO) {
        CouponVO couponVO = new CouponVO();
        BeanUtils.copyProperties(couponDO,couponVO);
        return couponVO;
    }
}
