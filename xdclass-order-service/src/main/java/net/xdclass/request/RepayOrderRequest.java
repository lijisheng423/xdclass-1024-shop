package net.xdclass.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RepayOrderRequest {

    /**
     * 订单号
     */
    @JsonProperty("out_trade_no")
    private String outTradeNo;

    /**
     * 支付方式
     */
    @JsonProperty("pay_type")
    private String payType;

    /**
     * 端类型
     */
    @JsonProperty("client_type")
    private String clientType;

}
