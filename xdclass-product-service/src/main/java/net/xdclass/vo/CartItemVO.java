package net.xdclass.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 购物项
 */
public class CartItemVO {
    /**
     * 商品id
     */
    @JsonProperty(value = "product_id")
    private Long productId;
    /**
     * 购买数量
     */
    @JsonProperty(value = "buy_num")
    private Integer buyNum;
    /**
     * 商品标题
     */
    @JsonProperty(value = "product_title")
    private String productTitle;
    /**
     * 图片
     */
    @JsonProperty(value = "product_img")
    private String productImg;
    /**
     * 商品单价
     */
    private BigDecimal amount;
    /**
     * 商品总价
     */
    @JsonProperty(value = "total_amount")
    private BigDecimal totalAmount;

    public Long getProductId() {
        return productId;
    }

    public Integer getBuyNum() {
        return buyNum;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public String getProductImg() {
        return productImg;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 商品单价 * 购买数量
     * @return
     */
    public BigDecimal getTotalAmount() {
        return this.amount.multiply(new BigDecimal(this.buyNum));
    }
}
