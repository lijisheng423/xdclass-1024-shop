package net.xdclass.model;

import lombok.Data;

@Data
public class OrderMessage {
    /**
     * 消息id
     */
    private long messageId;

    /**
     * 订单号
     */
    private String outTradeNo;

}
