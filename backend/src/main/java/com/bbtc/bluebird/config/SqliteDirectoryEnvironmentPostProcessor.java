package com.bbtc.bluebird.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 确保 SQLite 数据库文件所在目录存在（sqlite-jdbc 不会自动创建父目录）。
 *
 * <p>在 DataSource 初始化前解析 {@code SQLITE_URL} / {@code spring.datasource.url}，
 * 若为文件型 SQLite 则 mkdirs 其父目录。
 */
public class SqliteDirectoryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("SQLITE_URL");
        if (url == null || url.isBlank()) {
            url = environment.getProperty("spring.datasource.url");
        }
        if (url == null || !url.startsWith("jdbc:sqlite:")) {
            return;
        }
        String path = url.substring("jdbc:sqlite:".length());
        // 内存库 / 共享缓存内存库不落盘
        if (path.isBlank() || path.startsWith(":memory:") || path.contains("mode=memory")) {
            return;
        }
        File file = new File(path);
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.exists() && dir.mkdirs()) {
            Map<String, Object> props = new HashMap<>();
            props.put("bluebird.sqlite.dir.ready", "true");
            environment.getPropertySources().addFirst(new MapPropertySource("bluebird-sqlite-dir", props));
        }
    }

    @Override
    public int getOrder() {
        // 早于 DataSource 自动配置
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
