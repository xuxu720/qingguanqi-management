package com.qingguanqi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.qingguanqi.mapper")
public class QggApplication {

    public static void main(String[] args) {
        SpringApplication.run(QggApplication.class, args);
    }
}
