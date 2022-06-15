package net.xdclass.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

@Data
@ApiModel(value = "地址对象", description = "新增收货地址对象")
public class AddressAddRequest {

    /**
     * 是否默认收货地址：0->否；1->是
     */
    @ApiModelProperty(value = "是否默认收货地址：0->否；1->是", example = "0")
    @JsonProperty("default_status")
    private Integer defaultStatus;

    /**
     * 收发货人姓名
     */
    @ApiModelProperty(value = "收发货人姓名", example = "小滴课堂-王德发")
    @JsonProperty("receive_name")
    private String receiveName;

    /**
     * 收货人电话
     */
    @ApiModelProperty(value = "收货人电话", example = "18510874549")
    private String phone;

    /**
     * 省/直辖市
     */
    @ApiModelProperty(value = "省/直辖市", example = "北京市")
    private String province;

    /**
     * 市
     */
    @ApiModelProperty(value = "市", example = "北京市")
    private String city;

    /**
     * 区
     */
    @ApiModelProperty(value = "区", example = "朝阳区")
    private String region;

    /**
     * 详细地址
     */
    @ApiModelProperty(value = "详细地址", example = "丰台区海户西里")
    private String detailAddress;
}
