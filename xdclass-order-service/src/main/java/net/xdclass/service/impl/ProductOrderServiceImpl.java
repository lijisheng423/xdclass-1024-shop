package net.xdclass.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.exception.BizException;
import net.xdclass.fegin.ProductFeginService;
import net.xdclass.fegin.UserFeignService;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.ProductOrderMapper;
import net.xdclass.model.LoginUser;
import net.xdclass.model.ProductOrderDO;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.OrderItemVO;
import net.xdclass.vo.ProductOrderAddressVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProductOrderServiceImpl implements ProductOrderService {

    @Autowired
    private ProductOrderMapper productOrderMapper;

    @Autowired
    private UserFeignService userFeignService;

    @Autowired
    private ProductFeginService productFeginService;

    /**
     * 防重提交
     *
     * 用户微服务-确认收货地址
     *
     * 商品微服务-获取最新购物项和价格
     *
     * 订单验价
     *
     * 优惠券微服务-获取优惠券
     * 验证价格
     * 锁定优惠券
     *
     * 锁定商品库存
     *
     * 创建订单对象
     *
     * 创建子订单对象
     *
     * 发送延迟消息-用于自动关单
     *
     * 创建支付信息-对接三方支付
     * @param confirmOrderRequest
     * @return
     */
    @Override
    public JsonData confirmOrder(ConfirmOrderRequest confirmOrderRequest) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        String orderOutTradeNo = CommonUtil.getStringNumRandom(32);
        ProductOrderAddressVO productOrderAddressVO = this.getUserAddress(confirmOrderRequest.getAddressId());
        log.info("收货地址信息:{}",productOrderAddressVO);

        //获取用户加入购物车的商品
        List<Long> productIdList = confirmOrderRequest.getProductIdList();
        JsonData cartItemDate = productFeginService.confirmOrderCartItem(productIdList);
        List<OrderItemVO> orderItemVOList = cartItemDate.getData(new TypeReference<List<OrderItemVO>>(){});
        log.info("获取的商品:{}",orderItemVOList);
        if (null == orderItemVOList){
            //购物车商品不存在
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_CART_ITEM_NOT_EXIST);
        }


        return null;
    }

    /**
     * 获取收货地址详情
     * @param addressId
     * @return
     */
    private ProductOrderAddressVO getUserAddress(long addressId) {
        JsonData addressData = userFeignService.detail(addressId);
        if (addressData.getCode()!=0){
            log.error("获取收货地址失败，msg:{}",addressData);
            throw new BizException(BizCodeEnum.ADDRESS_NO_EXITS);
        }

        ProductOrderAddressVO productOrderAddressVO =  addressData.getData(new TypeReference<ProductOrderAddressVO>(){});
        return productOrderAddressVO;
    }

    /**
     * 查询订单状态
     * @param outTradeNo
     * @return
     */
    @Override
    public String queryProductOrderState(String outTradeNo) {
        ProductOrderDO productOrderDO = productOrderMapper.selectOne(new QueryWrapper<ProductOrderDO>()
                .eq("out_trade_no", outTradeNo));
        if (productOrderDO==null){
            return "";
        }else {
            return productOrderDO.getState();
        }
    }
}
