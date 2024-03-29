package net.xdclass.service;

import net.xdclass.model.OrderMessage;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.request.RepayOrderRequest;
import net.xdclass.util.JsonData;
import org.springframework.amqp.core.Message;

import java.util.Map;

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

    /**
     * 处理支付结果，回调通知
     * @param payType
     * @param paramsMap
     * @return
     */
    JsonData handlerOrderCallbackMsg(String payType, Map<String, String> paramsMap);

    /**
     * 分页查询我的订单列表
     * @param page
     * @param size
     * @param state
     * @return
     */
    Map<String, Object> page(int page, int size, String state);

    /**
     * 订单二次支付
     * @param repayOrderRequest
     * @return
     */
    JsonData repay(RepayOrderRequest repayOrderRequest);
}
