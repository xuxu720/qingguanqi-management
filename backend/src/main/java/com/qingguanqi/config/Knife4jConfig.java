package com.qingguanqi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("清管器作业管理系统 API")
                        .version("1.0.0")
                        .description("清管器作业管理系统后端接口文档")
                        .contact(new Contact().name("QingGuanQi")));
    }
}
