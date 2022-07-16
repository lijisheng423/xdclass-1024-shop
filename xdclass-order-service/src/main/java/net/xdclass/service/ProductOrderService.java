package net.xdclass.service;

import net.xdclass.model.OrderMessage;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.util.JsonData;
import org.springframework.amqp.core.Message;

public interface ProductOrderService {
    /**
     * 创建订单
     * @param confirmOrderRequest
     * @return
     */
    JsonData confirmOrder(ConfirmOrderRequest confirmOrderRequest);

    /**
     * 查询订单状态
     * @param outTradeNo
     * @return
     */
    String queryProductOrderState(String outTradeNo);

    /**
     * 关单队列监听，定时关单
     * @param message
     * @return
     */
    boolean closeProductOrder(OrderMessage message);
}
