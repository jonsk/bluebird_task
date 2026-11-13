package com.bbtc.bluebird.modules.file.infrastructure;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.util.IdGenerator;
import com.bbtc.bluebird.common.util.SizeText;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * 本地磁盘存储（02 §5.4）：{@code {root}/{yyyy}/{MM}/{snowflake}.{ext}}。
 * 校验大小/类型，流式 MD5，临时文件后原子移动，路径 sanitize 防穿越。
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    /** 与 ConfigDefinition 中 storage.max-size 的默认值保持一致。 */
    private static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;

    private final Path root;
    private final ConfigService config;

    public LocalFileStorage(@Value("${app.storage.root:./data/files}") String root, ConfigService config) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.config = config;
        // 首次启动确保存储根目录存在（用户要求：存储目录不存在则自动创建）
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            log.warn("初始化存储目录失败 {}：{}", this.root, e.getMessage());
        }
    }

    @Override
    public StoredFile store(String originalName, InputStream in, long size) {
        // 每次上传都读一次配置：管理页修改 storage.max-size / allowed-ext 后无需重启
        long maxBytes = SizeText.parseBytes(config.get("storage", "max-size"), DEFAULT_MAX_BYTES);
        Set<String> allowedExt = allowedExtensions();
        if (size > 0 && size > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = extension(originalName);
        if (!allowedExt.isEmpty() && !allowedExt.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOW);
        }
        LocalDate today = LocalDate.now();
        String dir = String.format("%04d/%02d", today.getYear(), today.getMonthValue());
        String rel = dir + "/" + IdGenerator.nextId() + (ext.isEmpty() ? "" : "." + ext);
        Path target = safeResolve(rel);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "up-", ".tmp");
            MessageDigest md = MessageDigest.getInstance("MD5");
            long written;
            try (DigestInputStream dis = new DigestInputStream(in, md);
                 OutputStream os = Files.newOutputStream(tmp)) {
                written = dis.transferTo(os);
            }
            if (written > maxBytes) {
                Files.deleteIfExists(tmp);
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            String md5 = HexFormat.of().formatHex(md.digest());
            return new StoredFile(rel, originalName, written, md5);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败：" + e.getMessage());
        }
    }

    @Override
    public Resource load(String relativePath) {
        Path p = safeResolve(relativePath);
        if (!Files.exists(p)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return new PathResource(p);
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(safeResolve(relativePath));
        } catch (IOException e) {
            log.warn("删除物理文件失败 {}：{}", relativePath, e.getMessage());
        }
    }

    private Path safeResolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法路径");
        }
        return p;
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Set<String> allowedExtensions() {
        String value = config.get("storage", "allowed-ext");
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Set.of(value.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
    }
}
