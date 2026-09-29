package com.bbtc.bluebird.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 运行时配置释放（ADR-010，02 §1.8.1）。
 *
 * <p>启动时若运行目录（CWD）无 {@code application.yml}，复制内置默认并预填自生成密钥；
 * 已存在则不覆盖。运行约定：CWD = jar 目录。
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.config.release", havingValue = "true", matchIfMissing = true)
public class ConfigFileReleaser implements ApplicationRunner {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void run(ApplicationArguments args) throws Exception {
        File target = new File("./application.yml");
        if (target.exists()) {
            log.info("运行目录已存在 application.yml，跳过释放（ADR-010 升级不覆盖）");
            return;
        }
        ClassPathResource resource = new ClassPathResource("application.yml");
        if (!resource.exists()) {
            return;
        }
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String jwt = randomBase64(32);
        String adminPwd = randomPassword();
        content = content.replace("${JWT_SECRET:}", jwt)
                .replace("${BOOTSTRAP_ADMIN_PASSWORD:}", adminPwd);
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
        restrict(target);
        log.warn("首次运行已释放 ./application.yml（含自动生成密钥，chmod 600）。");
        log.warn("初始 ADMIN 密码（仅本次打印，首次登录须改密）：{}", adminPwd);
    }

    private String randomBase64(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getEncoder().encodeToString(buf);
    }

    private String randomPassword() {
        byte[] buf = new byte[9];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private void restrict(File file) {
        try {
            boolean ok = file.setReadable(false, false);
            ok &= file.setReadable(true, true);
            ok &= file.setWritable(false, false);
            ok &= file.setWritable(true, true);
            if (!ok) {
                log.warn("无法收紧 {} 权限，请手动 chmod 600", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("设置配置文件权限失败：{}", e.getMessage());
        }
    }
}
