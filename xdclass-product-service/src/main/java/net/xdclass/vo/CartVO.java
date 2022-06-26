package net.xdclass.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车
 */
public class CartVO {

    /**
     * 购物项
     */
    @JsonProperty(value = "cart_items")
    private List<CartItemVO> cartItems;

    /**
     * 购买总件数
     */
    @JsonProperty(value = "total_num")
    private Integer totalNum;

    /**
     * 购物车总价格
     */
    @JsonProperty(value = "total_amount")
    private BigDecimal totalAmount;

    /**
     * 购物车实际支付价格
     */
    @JsonProperty(value = "real_pay_amount")
    private BigDecimal realPayAmount;

    public List<CartItemVO> getCartItems() {
        return cartItems;
    }

    /**
     * 总件数
     * @return
     */
    public Integer getTotalNum() {
        if (this.cartItems!=null){
            int total = cartItems.stream().mapToInt(CartItemVO::getBuyNum).sum();
            return total;
        }
        return 0;
    }

    /**
     * 总价格
     * @return
     */
    public BigDecimal getTotalAmount() {
        BigDecimal amount = new BigDecimal(0);
        if (this.cartItems!=null){
            for (CartItemVO cartItem : cartItems) {
                BigDecimal itemTotalAmount = cartItem.getTotalAmount();
                amount = amount.add(itemTotalAmount);
            }
        }
        return amount;
    }

    /**
     * 购物车里面实际支付价格
     * @return
     */
    public BigDecimal getRealPayAmount() {
        BigDecimal amount = new BigDecimal(0);
        if (this.cartItems!=null){
            for (CartItemVO cartItem : cartItems) {
                BigDecimal itemTotalAmount = cartItem.getTotalAmount();
                amount = amount.add(itemTotalAmount);
            }
        }
        return amount;
    }

    public void setCartItems(List<CartItemVO> cartItems) {
        this.cartItems = cartItems;
    }

    public void setTotalNum(Integer totalNum) {
        this.totalNum = totalNum;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setRealPayAmount(BigDecimal realPayAmount) {
        this.realPayAmount = realPayAmount;
    }
}
