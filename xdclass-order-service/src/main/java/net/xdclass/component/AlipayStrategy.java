package net.xdclass.component;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.AlipayConfig;
import net.xdclass.config.PayUrlConfig;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.ClientType;
import net.xdclass.exception.BizException;
import net.xdclass.vo.PayInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.UUID;

@Service
@Slf4j
public class AlipayStrategy implements PayStrategy {

    @Autowired
    private PayUrlConfig payUrlConfig;

    @Override
    public String unifiedorder(PayInfoVO payInfoVO) {
        HashMap<String,String> content = new HashMap<>();

        //商户订单号,64个字符以内、可包含字母、数字、下划线；需保证在商户端不重复
        content.put("out_trade_no", payInfoVO.getOutTradeNo());
        content.put("product_code", "FAST_INSTANT_TRADE_PAY");
        //订单总金额，单位为元，精确到小数点后两位
        content.put("total_amount", payInfoVO.getPayFee().toString());
        //商品标题/交易标题/订单标题/订单关键字等。 注意：不可使用特殊字符，如 /，=，&amp; 等。
        content.put("subject", payInfoVO.getTitle());
        //商品描述，可空
        content.put("body", payInfoVO.getDescription());

        // 该笔订单允许的最晚付款时间，逾期将关闭交易。取值范围：1m～15d。m-分钟，h-小时，d-天，1c-当天（1c-当天的情况下，无论交易何时创建，都在0点关闭）。 该参数数值不接受小数点， 如 1.5h，可转换为 90m。
        double timeOut = Math.floor(payInfoVO.getOrderPayTimeoutMills() / (1000 * 60));
        //前端也需要判断订单是否要关闭，如果快要到期则不给二次支付
        if (timeOut<1){
            throw new BizException(BizCodeEnum.PAY_ORDER_PAY_TIMEOUT);
        }
        content.put("timeout_express", Double.valueOf(timeOut)+"m");

        String clientType = payInfoVO.getClientType();
        String form = "";

        try{
            if (clientType.equalsIgnoreCase(ClientType.H5.name())){
                //H5手机网页支付
                AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
                request.setBizContent(JSON.toJSONString(content));
                request.setNotifyUrl(payUrlConfig.getAlipayCallbackUrl());
                request.setReturnUrl(payUrlConfig.getAlipaySuccessReturnUrl());
                AlipayTradeWapPayResponse alipayResponse = AlipayConfig.getInstance().pageExecute(request);
                log.info("H5响应日志：alipayResponse：{}",alipayResponse);
                if(alipayResponse.isSuccess()){
                    form = alipayResponse.getBody();
                } else {
                    log.error("支付宝构建H5表单失败:response:{},payInfo:{}",alipayResponse,payInfoVO);
                }
            }else if (clientType.equalsIgnoreCase(ClientType.PC.name())){
                //PC支付
                AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
                request.setBizContent(JSON.toJSONString(content));
                request.setNotifyUrl(payUrlConfig.getAlipayCallbackUrl());
                request.setReturnUrl(payUrlConfig.getAlipaySuccessReturnUrl());

                AlipayTradePagePayResponse alipayResponse = AlipayConfig.getInstance().pageExecute(request);
                log.info("PC响应日志：alipayResponse：{}",alipayResponse);
                if(alipayResponse.isSuccess()){
                    form = alipayResponse.getBody();
                } else {
                    log.error("支付宝构建PC表单失败:response:{},payInfo:{}",alipayResponse,payInfoVO);
                }
            }

        }catch (AlipayApiException e){
            log.error("支付宝构建表单异常，payInfo:{},e:{}",payInfoVO,e.getMessage());
        }
        return form;
    }

    @Override
    public String refund(PayInfoVO payInfoVO) {
        return null;
    }

    @Override
    public String queryPaySuccess(PayInfoVO payInfoVO) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        HashMap<String,String> content = new HashMap<>();
        //订单商户号，64位
        content.put("out_trade_no",payInfoVO.getOutTradeNo());
        request.setBizContent(JSON.toJSONString(content));
        AlipayTradeQueryResponse queryResponse = null;
        try {
            queryResponse = AlipayConfig.getInstance().execute(request);
            log.info("订单查询响应:{}",queryResponse.getBody());
        } catch (AlipayApiException e) {
            log.error("支付宝订单查询异常:{}",e.getMessage());
        }

        if (queryResponse.isSuccess()){
            log.info("支付宝订单状态查询成功:{}",payInfoVO);
            return queryResponse.getTradeStatus();
        }else {
            log.info("支付宝订单状态查询失败:{}",payInfoVO);
            return "";
        }
    }
}
