package com.bbtc.bluebird;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BlueBird Task 应用入口。
 *
 * <p>单制品 jar（内嵌前端，ADR-009）；SQLite 嵌入式、无缓存层（ADR-016）。
 * 启动约定：CWD = jar 所在目录（配置释放与读取同一目录，ADR-010）。
 */
@SpringBootApplication
@MapperScan({
        "com.bbtc.bluebird.modules.identity.infrastructure",
        "com.bbtc.bluebird.modules.audit.infrastructure"
})
@EnableAsync
@EnableScheduling
public class BlueBirdApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlueBirdApplication.class, args);
    }
}
