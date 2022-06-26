package net.xdclass.service.impl;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.constant.CacheKey;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.exception.BizException;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.model.LoginUser;
import net.xdclass.request.CartItemRequest;
import net.xdclass.service.CartService;
import net.xdclass.service.ProductService;
import net.xdclass.vo.CartItemVO;
import net.xdclass.vo.CartVO;
import net.xdclass.vo.ProductVO;
import org.apache.commons.lang3.StringUtils;
import org.mockito.internal.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements CartService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductService productService;

    /**
     * 添加商品到购物车
     * @param cartItemRequest
     */
    @Override
    public void addToCart(CartItemRequest cartItemRequest) {
        long productId = cartItemRequest.getProductId();
        int buyNum = cartItemRequest.getBuyNum();
        //获取购物车
        BoundHashOperations<String,Object,Object> myCart = getMyCartOps();
        Object cacheObj = myCart.get(productId);
        String result = "";

        if (cacheObj != null){
            result = (String) cacheObj;
        }

        if (StringUtils.isBlank(result)){
            //不存在则新建一个购物项
            CartItemVO cartItemVO = new CartItemVO();
            ProductVO productVO = productService.findDetailById(productId);
            if (productVO==null){
                throw new BizException(BizCodeEnum.CART_FAIL);
            }
            cartItemVO.setAmount(productVO.getAmount());
            cartItemVO.setBuyNum(buyNum);
            cartItemVO.setProductId(productId);
            cartItemVO.setProductImg(productVO.getCoverImg());
            cartItemVO.setProductTitle(productVO.getTitle());
            myCart.put(productId, JSON.toJSONString(cartItemVO));
        }else {
            //存在则新增数量
            CartItemVO cartItemVO = JSON.parseObject(result, CartItemVO.class);
            cartItemVO.setBuyNum(cartItemVO.getBuyNum()+buyNum);
            myCart.put(productId,JSON.toJSONString(cartItemVO));
        }

    }

    /**
     * 清空购物车
     */
    @Override
    public void clear() {
        String cartKey = getCartKey();
        redisTemplate.delete(cartKey);
    }

    /**
     * 查看我的购物车
     * @return
     */
    @Override
    public CartVO getMyCart() {
        //获取全部购物项
        List<CartItemVO> cartItemVOList = buildCartItem(true);

        //封装成cartVO
        CartVO cartVO = new CartVO();
        cartVO.setCartItems(cartItemVOList);

        return cartVO;
    }

    /**
     * 删除购物项
     * @param productId
     */
    @Override
    public void deleteItem(long productId) {

        BoundHashOperations<String, Object, Object> myCart = getMyCartOps();

        myCart.delete(productId);

    }

    /**
     * 修改购物车商品数量
     * @param cartItemRequest
     */
    @Override
    public void changeItemNum(CartItemRequest cartItemRequest) {

        BoundHashOperations<String, Object, Object> myCart = getMyCartOps();

        Object obj = myCart.get(cartItemRequest.getProductId());
        if (obj==null){
            throw new BizException(BizCodeEnum.CART_FAIL);
        }
        CartItemVO cartItemVO = JSON.parseObject((String) obj, CartItemVO.class);
        cartItemVO.setBuyNum(cartItemRequest.getBuyNum());
        myCart.put(cartItemRequest.getProductId(),JSON.toJSONString(cartItemVO));

    }


    /**
     * 获取最新的购物项
     * @param latestPrice 是否回去最新的价格
     * @return
     */
    private List<CartItemVO> buildCartItem(boolean latestPrice) {

        BoundHashOperations<String,Object,Object> myCart = getMyCartOps();

        List<Object> itemList = myCart.values();

        List<CartItemVO> cartItemVOList = new ArrayList<>();

        //拼接id列表查询最新价格
        List<Long> productIdList = new ArrayList<>();

        for (Object item : itemList) {
            CartItemVO cartItemVO = JSON.parseObject((String) item,CartItemVO.class);
            cartItemVOList.add(cartItemVO);

            productIdList.add(cartItemVO.getProductId());
        }
        //查询最新的商品价格
        if (latestPrice){
            setProductLatestPrice(cartItemVOList,productIdList);
        }

        return cartItemVOList;
    }

    /**
     * 设置商品最新价格
     * @param cartItemVOList
     * @param productIdList
     */
    private void setProductLatestPrice(List<CartItemVO> cartItemVOList, List<Long> productIdList) {
        //批量查询
        List<ProductVO> productVOList = productService.findProductsByIdBatch(productIdList);
        //分组
        Map<Long, ProductVO> productVOMap = productVOList.stream()
                .collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        cartItemVOList.stream().forEach(item->{
            ProductVO productVO = productVOMap.get(item.getProductId());
            item.setProductTitle(productVO.getTitle());
            item.setProductImg(productVO.getCoverImg());
            item.setAmount(productVO.getAmount());
        });
    }

    /**
     * 抽取我的购物车通用方法
     * @return
     */
    private BoundHashOperations<String, Object, Object> getMyCartOps() {
        String cartKey = getCartKey();
        return redisTemplate.boundHashOps(cartKey);
    }

    /**
     * 获取购物车的key
     * @return
     */
    private String getCartKey() {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        String cartKey = String.format(CacheKey.CART_KEY, loginUser.getId());
        return cartKey;
    }
}
