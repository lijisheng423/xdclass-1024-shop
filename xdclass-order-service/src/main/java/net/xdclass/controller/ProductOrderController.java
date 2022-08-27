package net.xdclass.controller;


import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.AlipayConfig;
import net.xdclass.config.PayUrlConfig;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.ClientType;
import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.JsonData;
import org.apache.commons.lang3.StringUtils;
import org.mockito.internal.util.StringUtil;
import org.redisson.misc.Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 二当家小D
 * @since 2022-06-27
 */
@Api(tags = "订单模块")
@RestController
@RequestMapping("/api/order/v1")
@Slf4j
public class ProductOrderController {

    @Autowired
    private ProductOrderService productOrderService;

    @Autowired
    private PayUrlConfig payUrlConfig;

    /**
     * 分页查询我的订单列表
     * @param page
     * @param size
     * @param state
     * @return
     */
    @ApiOperation("分页查询我的订单列表")
    @GetMapping("page")
    public JsonData pageOrderList(@ApiParam(value = "当前页") @RequestParam(value = "page", defaultValue = "1") int page,
                                  @ApiParam(value = "每页显示多少条") @RequestParam(value = "size", defaultValue = "2") int size,
                                  @ApiParam(value = "订单状态") @RequestParam(value = "state", required = false) String state
                                  ){

        Map<String,Object> pageResult = productOrderService.page(page,size,state);
        return JsonData.buildSuccess(pageResult);
    }



    /**
     * 此接口没有登录拦截，可以增加一个秘钥进行rpc通信
     * @param outTradeNo
     * @return
     */
    @ApiOperation("查询订单状态")
    @GetMapping("query_state")
    public JsonData queryProductOrderState(@ApiParam("订单号") @RequestParam("out_trade_no") String outTradeNo){
        String state = productOrderService.queryProductOrderState(outTradeNo);
        return StringUtils.isBlank(state)?JsonData.buildResult(BizCodeEnum.ORDER_CONFIRM_NOT_EXIST):JsonData.buildSuccess(state);
    }

    @ApiOperation("提交订单")
    @PostMapping("confirm")
    private void  confirmOrder(@ApiParam("订单对象") @RequestBody ConfirmOrderRequest confirmOrderRequest, HttpServletResponse response){

        JsonData jsonData = productOrderService.confirmOrder(confirmOrderRequest);

        if (jsonData.getCode()==0){
            String clientType = confirmOrderRequest.getClientType();
            String payType = confirmOrderRequest.getPayType();
            //支付宝网页支付，都是跳转网页，APP除外
            if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.ALIPAY.name())){
                log.info("创建支付宝订单成功:{}",confirmOrderRequest.toString());
                if (clientType.equalsIgnoreCase(ClientType.H5.name())){
                    writeData(response,jsonData);
                }else if (clientType.equalsIgnoreCase(ClientType.APP.name())){
                    //APP SDK支付 todo
                }

            }else if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.WECHAT.name())){
                //微信支付 todo
            }
        }else {
            log.error("创建订单失败：{}",jsonData.toString());
        }
    }

    private void writeData(HttpServletResponse response, JsonData jsonData) {
        try {
            response.setContentType("text/html;charset=UTF8");
            response.getWriter().write(jsonData.getData().toString());
            response.getWriter().flush();
            response.getWriter().close();
        }catch (IOException e){
            log.error("写出html异常:{}",e);
        }
    }


    @GetMapping("test_pay")
    public void testAlipay(HttpServletResponse response) throws AlipayApiException, IOException {
        HashMap<String,String> content = new HashMap<>();

        //商户订单号,64个字符以内、可包含字母、数字、下划线；需保证在商户端不重复
        String no = UUID.randomUUID().toString();
        log.info("订单号:{}",no);
        content.put("out_trade_no", no);

        content.put("product_code", "FAST_INSTANT_TRADE_PAY");

        //订单总金额，单位为元，精确到小数点后两位
        content.put("total_amount", String.valueOf("111.99"));

        //商品标题/交易标题/订单标题/订单关键字等。 注意：不可使用特殊字符，如 /，=，&amp; 等。
        content.put("subject", "杯子");

        //商品描述，可空
        content.put("body", "好的杯子");

        // 该笔订单允许的最晚付款时间，逾期将关闭交易。取值范围：1m～15d。m-分钟，h-小时，d-天，1c-当天（1c-当天的情况下，无论交易何时创建，都在0点关闭）。 该参数数值不接受小数点， 如 1.5h，可转换为 90m。
        content.put("timeout_express", "5m");

        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();

        request.setBizContent(JSON.toJSONString(content));
        request.setNotifyUrl(payUrlConfig.getAlipayCallbackUrl());
        request.setReturnUrl(payUrlConfig.getAlipaySuccessReturnUrl());

        AlipayTradeWapPayResponse alipayResponse = AlipayConfig.getInstance().pageExecute(request);
        if(alipayResponse.isSuccess()){
            System.out.println("调用成功");
            String form = alipayResponse.getBody();
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(form);
            response.getWriter().flush();
            response.getWriter().close();
        } else {
            System.out.println("调用失败");
        }
    }



}

