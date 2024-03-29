package net.xdclass.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemVO {
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

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setBuyNum(Integer buyNum) {
        this.buyNum = buyNum;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public void setProductImg(String productImg) {
        this.productImg = productImg;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "OrderItemVO{" +
                "productId=" + productId +
                ", buyNum=" + buyNum +
                ", productTitle='" + productTitle + '\'' +
                ", productImg='" + productImg + '\'' +
                ", amount=" + amount +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
