package com.bbtc.bluebird;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
 *
 * <p><b>mapper 扫描范围</b>：仅注册继承 {@link BaseMapper} 的接口。不能用裸包名扫描，
 * 否则 {@code modules.*.infrastructure} 下的非 mapper 接口（如 {@code FileStorage} 存储抽象）
 * 会被 MyBatis 注册为 mapper，运行期调用时报
 * {@code BindingException: Invalid bound statement (not found)}（附件上传 10000 的根因）。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.bbtc.bluebird.modules", markerInterface = BaseMapper.class)
@EnableAsync
@EnableScheduling
public class BlueBirdApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlueBirdApplication.class, args);
    }
}
