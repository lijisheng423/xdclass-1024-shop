package net.xdclass.model;

import lombok.Data;

@Data
public class CouponRecordMessage {

    /**
     * 消息id
     */
    private long messageId;

    /**
     * 订单号
     */
    private String outTradeNo;

    /**
     * 库存锁定任务id
     */
    private Long taskId;
}
