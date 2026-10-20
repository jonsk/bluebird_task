package com.bbtc.bluebird.modules.config.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置密文加解密（AES-256-GCM）。
 *
 * <p>主密钥来源优先级：环境变量/属性 {@code app.config.secret}（Base64 32 字节，或任意口令经
 * SHA-256 派生）→ 密钥文件 {@code app.config.key-file}（首次启动自动生成，默认
 * {@code ./data/.config-key}）。**主密钥不入库、不进 git**，库中只存密文。
 *
 * <p>密文格式：{@code Base64(iv[12] || ciphertext+tag)}。
 */
@Slf4j
@Component
public class ConfigCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public ConfigCrypto(
            @Value("${app.config.secret:}") String secret,
            @Value("${app.config.key-file:./data/.config-key}") String keyFile) {
        this.key = new SecretKeySpec(loadKey(secret, keyFile), "AES");
    }

    /** 加密；入参为空返回 null。 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("配置加密失败", e);
        }
    }

    /** 解密；密文损坏或主密钥变更时返回 null（避免整个配置页因一条脏数据不可用）。 */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length <= IV_LEN) {
                return null;
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("配置解密失败（主密钥可能已更换）：{}", e.getMessage());
            return null;
        }
    }

    private byte[] loadKey(String secret, String keyFile) {
        if (secret != null && !secret.isBlank()) {
            byte[] decoded = tryBase64(secret);
            if (decoded != null && decoded.length == 32) {
                return decoded;
            }
            return sha256(secret);
        }
        try {
            Path path = Path.of(keyFile);
            if (Files.exists(path)) {
                byte[] decoded = tryBase64(Files.readString(path, StandardCharsets.UTF_8).trim());
                if (decoded != null && decoded.length == 32) {
                    return decoded;
                }
            }
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, Base64.getEncoder().encodeToString(generated), StandardCharsets.UTF_8);
            log.info("已生成配置加密主密钥文件：{}", path.toAbsolutePath());
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化配置加密主密钥（请设置 BB_CONFIG_SECRET）", e);
        }
    }

    private byte[] tryBase64(String value) {
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
