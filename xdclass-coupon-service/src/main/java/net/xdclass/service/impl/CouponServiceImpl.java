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
import net.xdclass.service.CouponService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CouponVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;
    
    @Autowired
    private CouponRecordMapper couponRecordMapper;

    @Autowired
    private RedisTemplate redisTemplate;

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
    @Override
    public JsonData addCoupon(long couponId, CouponCategoryEnum categoryEnum) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        /**
         * 原生分布式锁 开始
         * 1.原子加锁 设置过期时间，防止宕机死锁
         * 2.原子解锁：需要判断是不是自己的锁
         */
        String uuid = CommonUtil.generateUUID();
        String lockKey = "lock:coupon:" + couponId;
        //避免锁过期，一般配置久一点
        Boolean lockFlag = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, Duration.ofMillis(10));
        if (lockFlag) {
            log.info("加锁成功:{}", couponId);
            try {
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
                if (rows == 1) {
                    //扣减库存成功才保存记录
                    couponRecordMapper.insert(couponRecordDO);
                } else {
                    log.warn("发放优惠券失败：{}，用户：{}", couponDO, loginUser);
                    throw new BizException(BizCodeEnum.COUPON_NO_STOCK);
                }
            } finally {
                //解锁
                String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
                Integer result = (Integer) redisTemplate.execute(new DefaultRedisScript(script, Integer.class), Arrays.asList(lockKey), uuid);
                log.info("解锁:{}", result);
            }
        }else {
            //加锁失败，睡眠100毫秒，自旋重试
            try {
                TimeUnit.MILLISECONDS.sleep(100L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return addCoupon(couponId,categoryEnum);
        }

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
