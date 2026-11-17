package com.fyreader.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 风月读书 lite 版后端服务入口。
 * <p>替代客户端爬虫解析链路，统一对外提供书籍数据接口。</p>
 */
@SpringBootApplication
@MapperScan("com.fyreader.service.mapper")
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }
}
