package net.xdclass.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.CouponCategoryEnum;
import net.xdclass.service.CouponService;
import net.xdclass.util.JsonData;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 二当家小D
 * @since 2022-06-21
 */
@Api(tags = "优惠券模块")
@RestController
@Slf4j
@RequestMapping("/api/coupon/v1")
public class CouponController {

    @Autowired
    private CouponService couponService;


    @ApiOperation("分页查询优惠券")
    @GetMapping("page_coupon")
    public JsonData pageCouponList(@ApiParam(value = "当前页") @RequestParam(value = "page", defaultValue = "1") int page,
                                   @ApiParam(value = "每页显示多少条") @RequestParam(value = "size", defaultValue = "2") int size) {

        Map<String, Object> pageMap = couponService.pageCouponActivity(page, size);
        return JsonData.buildSuccess(pageMap);

    }

    @ApiOperation("领取优惠券")
    @GetMapping("add/promotion/{coupon_id}")
    public JsonData addPromotionCoupon(@ApiParam(value = "优惠券ID",required = true) @PathVariable("coupon_id") long couponId){
        JsonData jsonData = couponService.addCoupon(couponId, CouponCategoryEnum.PROMOTION);
        return JsonData.buildSuccess();
    }
}

