package net.xdclass.fegin;

import net.xdclass.request.LockCouponRecordRequest;
import net.xdclass.util.JsonData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "xdclass-coupon-service")
public interface CouponFeignService {

    /**
     * 查询用户的优惠券是否可用，需要防止水平权限
     * @param couponRecordId
     * @return
     */
    @GetMapping("/api/coupon_record/v1/detail/{record_id}")
    JsonData findUserCouponRecordById(@PathVariable("record_id") long couponRecordId);

    @PostMapping("/api/coupon_record/v1/lock_records")
    JsonData lockCouponRecords(@RequestBody LockCouponRecordRequest lockCouponRecordRequest);
}
