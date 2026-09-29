package com.bbtc.bluebird.config;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * 逻辑删除防误加断言（02 §1.6 R3）。
 *
 * <p>全局 {@code logic-delete-field=deleted} 会作用于任何含 {@code deleted} 字段的 DO；
 * 关系/从属/日志表无 {@code deleted} 列，其 DO 一旦误加该字段，MP 会拼 {@code WHERE deleted=0} → SQL 报错。
 * 启动期反射扫描断言，含则 fail-fast。
 */
@Slf4j
@Component
@Profile("!test")
public class LogicDeleteGuard implements ApplicationRunner {

    /** 禁止声明 deleted 字段的表（关系/从属/日志表）。 */
    private static final Set<String> NO_DELETED_TABLES = Set.of(
            "task_participant", "tag", "task_tag", "task_collect", "task_menu", "task_menu_item",
            "sys_user_department", "attachment", "audit_operate_log", "audit_login_log", "sys_refresh_token");

    private static final String PACKAGE = "com.bbtc.bluebird";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var readerFactory = new CachingMetadataReaderFactory(resolver);
        var resources = resolver.getResources("classpath*:" + PACKAGE.replace('.', '/') + "/**/*.class");
        int checked = 0;
        for (var res : resources) {
            try (var is = res.getInputStream()) {
                MetadataReader mr = readerFactory.getMetadataReader(res);
                String className = mr.getClassMetadata().getClassName();
                Class<?> clazz = Class.forName(className);
                TableName tn = clazz.getAnnotation(TableName.class);
                if (tn == null) {
                    continue;
                }
                String table = tn.value();
                if (NO_DELETED_TABLES.contains(table)) {
                    checked++;
                    for (Field f : clazz.getDeclaredFields()) {
                        if ("deleted".equals(f.getName())) {
                            throw new IllegalStateException(
                                    "LogicDeleteGuard: 表 " + table + " 的 DO " + className + " 不得声明 deleted 字段");
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // 跳过无法加载的类
            }
        }
        log.info("LogicDeleteGuard 校验通过（检查 {} 个无 deleted 列的表）", checked);
    }
}
