package net.xdclass.service;

import java.util.Map;

public interface CouponRecordService {

    /**
     * 分页查询领券记录
     * @param page
     * @param size
     * @return
     */
    Map<String,Object> page(int page, int size);
}
