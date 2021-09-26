package net.xdclass.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(value = "用戶注册对象", description = "用户注册请求对象")
@Data
public class UserRegisterRequest {

    @ApiModelProperty(value = "昵称", example = "Anna小姐")
    private String name;

    @ApiModelProperty(value = "密码", example = "123456")
    private String pwd;

    @ApiModelProperty(value = "头像", example = "https://xdclass-test-img.oss-cn-beijing.aliyuncs.com/user/2021/09/26/0395c81ddb4b41cc8d31f34705c4ab8f.jpg")
    @JsonProperty("head_img")
    private String headImg;

    @ApiModelProperty(value = "用户个性签名", example = "道阻且长，行则将至。")
    private String slogan;

    @ApiModelProperty(value = "0表示女，1表示男", example = "1")
    private Integer sex;

    @ApiModelProperty(value = "邮箱", example = "1324549476@qq.com")
    private String mail;

    @ApiModelProperty(value = "验证码", example = "952700")
    private String code;

}
