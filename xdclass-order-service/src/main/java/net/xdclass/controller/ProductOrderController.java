package net.xdclass.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.ClientType;
import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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

}

