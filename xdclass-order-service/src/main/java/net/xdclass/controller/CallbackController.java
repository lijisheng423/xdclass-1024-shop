package net.xdclass.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.AlipayConfig;
import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Controller
@ApiOperation("订单回调通知模块")
@RequestMapping("api/callback/order/v1")
@Slf4j
public class CallbackController {

    @Autowired
    private ProductOrderService productOrderService;

    /**
     * 支付回调通知，post方式
     * @return
     */
    @PostMapping("alipay")
    public String alipayCallback(HttpServletRequest request, HttpServletResponse response){
        //将异步通知中收到的所有参数存储到Map中
        Map<String, String> paramsMap = convertRequestParamsToMap(request);
        log.info("支付宝回调通知结果:{}",paramsMap);
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(paramsMap, AlipayConfig.ALIPAY_PUB_KEY, AlipayConfig.CHARSET, AlipayConfig.SIGN_TYPE); //调用SDK验证签名
            if(signVerified){
                // 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，
                // 校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
                JsonData jsonData = productOrderService.handlerOrderCallbackMsg(ProductOrderPayTypeEnum.ALIPAY.name(),paramsMap);
                if (jsonData.getCode()==0){
                    //通知结果表确认成功，不然会一直通知，8次都每返回success，确认交易失败
                    return "SUCCESS";
                }
            }else{
                // TODO 验签失败则记录异常日志，并在response中返回failure.
            }
        } catch (AlipayApiException e) {
            log.info("支付宝回调验签失败 paramsMap:{},e",paramsMap,e.getMessage());
        }
        return "failure";
    }


    /**
     * 将request中的参数转换成Map
     * @param request
     * @return
     */
    private static Map<String, String> convertRequestParamsToMap(HttpServletRequest request) {
        Map<String, String> paramsMap = new HashMap<>(16);
        Set<Map.Entry<String, String[]>> entrySet = request.getParameterMap().entrySet();
        for (Map.Entry<String, String[]> entry : entrySet) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            int size = values.length;
            if (size == 1) {
                paramsMap.put(name, values[0]);
            } else {
                paramsMap.put(name, "");
            }
        }
        System.out.println(paramsMap);
        return paramsMap;
    }


}
