package net.xdclass.config;

import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Component
@Data
@EnableOpenApi
public class SwaggerConfiguration {

    @Bean
    public Docket webApiDoc(){

        return new Docket(DocumentationType.OAS_30)
                .groupName("用户端接口文档")
                .pathMapping("/")
                //定义是否开启swagger,false为关闭，可以通过变量控制，线上关闭
                .enable(true)
                //配置api文档元信息
                .apiInfo(apiInfo())
                //选择哪些接口作为swagger的doc发布
                .select().apis(RequestHandlerSelectors.basePackage("net.xdclass"))
                //正则匹配请求路径，并分配至当前分组
                .paths(PathSelectors.ant("/api/**"))
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("1024电商平台")
                .description("微服务接口文档")
                .contact(new Contact("小滴课堂-ljs","https://www.baidu.com","132454@qq.com"))
                .version("12")
                .build();
    }
}
