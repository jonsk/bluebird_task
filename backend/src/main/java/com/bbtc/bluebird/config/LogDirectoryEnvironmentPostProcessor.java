package com.bbtc.bluebird.config;

import com.bbtc.bluebird.BlueBirdApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析运行日志目录，供 {@code logback-spring.xml} 使用（在 Logback 初始化前执行）。
 *
 * <p>规则：配置文件 {@code app.log.dir} 指定则使用（相对路径按启动工作目录解析）；
 * 未指定则默认 <b>jar 所在目录下的 {@code log} 目录</b>（从 IDE / 测试启动时退回 {@code user.dir/log}）。
 * 目录不存在时自动创建。结果以 {@code bluebird.log.dir} / {@code bluebird.log.max-history-days}
 * 注入 Spring 环境与系统属性，供日志配置引用。
 */
public class LogDirectoryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String APP_LOG_DIR = "app.log.dir";
    public static final String APP_LOG_MAX_HISTORY = "app.log.max-history-days";
    public static final String PROP_LOG_DIR = "bluebird.log.dir";
    public static final String PROP_LOG_MAX_HISTORY = "bluebird.log.max-history-days";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configured = environment.getProperty(APP_LOG_DIR);
        File dir;
        if (configured != null && !configured.isBlank()) {
            dir = new File(configured);
            if (!dir.isAbsolute()) {
                dir = new File(System.getProperty("user.dir"), configured);
            }
        } else {
            dir = defaultLogDir();
        }

        String abs = dir.getAbsolutePath();
        if (!dir.exists() && !dir.mkdirs()) {
            // 目录创建失败：退回 user.dir/log，避免日志落盘失败静默丢日志
            File fallback = new File(System.getProperty("user.dir"), "log");
            fallback.mkdirs();
            abs = fallback.getAbsolutePath();
        }

        String maxHistory = environment.getProperty(APP_LOG_MAX_HISTORY);
        if (maxHistory == null || maxHistory.isBlank()) {
            maxHistory = "30";
        }

        // 同时写系统属性（logback 的 ${...} 可直接解析）与 Spring 环境（springProperty 可读）
        System.setProperty(PROP_LOG_DIR, abs);
        System.setProperty(PROP_LOG_MAX_HISTORY, maxHistory);

        Map<String, Object> props = new HashMap<>();
        props.put(PROP_LOG_DIR, abs);
        props.put(PROP_LOG_MAX_HISTORY, maxHistory);
        environment.getPropertySources().addFirst(new MapPropertySource("bluebird-log-dir", props));
    }

    /** 默认目录：jar 所在目录 / log；从 classes 启动（IDE/test）时退回 user.dir/log。 */
    private File defaultLogDir() {
        File jarDir = jarDirectory();
        if (jarDir != null) {
            return new File(jarDir, "log");
        }
        return new File(System.getProperty("user.dir"), "log");
    }

    /** 定位 jar 真实所在目录；若从 classes 目录启动则返回 null。 */
    private File jarDirectory() {
        // java -jar 时 java.class.path 即该 jar 路径（最可靠，兼容 Spring Boot fat jar）
        File fromClassPath = jarFromClassPath();
        if (fromClassPath != null) {
            return fromClassPath;
        }
        // 兜底：代码源定位（对非 fat jar / 直启场景）
        try {
            ProtectionDomain pd = BlueBirdApplication.class.getProtectionDomain();
            if (pd == null) {
                return null;
            }
            CodeSource cs = pd.getCodeSource();
            if (cs == null) {
                return null;
            }
            URL location = cs.getLocation();
            if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
                return null; // Spring Boot fat jar 为 nested: 协议，走 java.class.path
            }
            File f = new File(location.toURI());
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                return f.getParentFile();
            }
            return null; // classes 目录（IDE/test）→ 退回 user.dir/log，避免污染构建产物
        } catch (Exception ignored) {
            return null;
        }
    }

    private File jarFromClassPath() {
        String cp = System.getProperty("java.class.path");
        if (cp == null || cp.isBlank()) {
            return null;
        }
        String first = cp.split(Pattern.quote(File.pathSeparator))[0];
        File f = new File(first);
        if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
            return f.getParentFile();
        }
        return null;
    }

    @Override
    public int getOrder() {
        // 必须晚于 ConfigDataEnvironmentPostProcessor（HIGHEST_PRECEDENCE + 10）——它加载 application.yml，
        // 否则 app.log.dir 尚未就绪，LOG_DIR 覆盖不生效。取 +30（晚于同类的 SqliteDirectory +20，且早于 Logback 初始化）。
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
