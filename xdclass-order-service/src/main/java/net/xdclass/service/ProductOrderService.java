package net.xdclass.service;

import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.util.JsonData;

public interface ProductOrderService {
    /**
     * 创建订单
     * @param confirmOrderRequest
     * @return
     */
    JsonData confirmOrder(ConfirmOrderRequest confirmOrderRequest);
}
