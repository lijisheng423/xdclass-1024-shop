package net.xdclass.service;

import net.xdclass.enums.CouponCategoryEnum;
import net.xdclass.request.NewUserRequest;
import net.xdclass.util.JsonData;

import java.util.Map;

public interface CouponService {
    /**
     * 分页查询优惠券
     * @param page
     * @param size
     * @return
     */
    Map<String,Object> pageCouponActivity(int page, int size);

    /**
     * 领取优惠券接口
     * @param couponId
     * @param categoryEnum
     * @return
     */
    JsonData addCoupon(long couponId, CouponCategoryEnum categoryEnum);

    /**
     * 新用户注册发送优惠券
     * @param newUserRequest
     * @return
     */
    JsonData initNewUserCoupon(NewUserRequest newUserRequest);
}
