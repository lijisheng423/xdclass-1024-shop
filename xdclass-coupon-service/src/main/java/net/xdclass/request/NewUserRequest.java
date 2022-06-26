package net.xdclass.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel
@Data
public class NewUserRequest {

    @ApiModelProperty(value = "用户ID", example = "19")
    private long userId;

    @ApiModelProperty(value = "名称", example = "星源")
    private String name;
}
