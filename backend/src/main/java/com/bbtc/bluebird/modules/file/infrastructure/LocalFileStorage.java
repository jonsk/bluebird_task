package com.bbtc.bluebird.modules.file.infrastructure;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.util.IdGenerator;
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

    private final Path root;
    private final long maxBytes;
    private final Set<String> allowedExt;

    public LocalFileStorage(@Value("${app.storage.root:./data/files}") String root,
                            @Value("${app.storage.max-size:50MB}") String maxSize,
                            @Value("${app.storage.allowed-ext:}") String allowedExt) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.maxBytes = parseSize(maxSize);
        this.allowedExt = allowedExt == null || allowedExt.isBlank()
                ? Set.of()
                : Set.of(allowedExt.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        // 首次启动确保存储根目录存在（用户要求：存储目录不存在则自动创建）
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            log.warn("初始化存储目录失败 {}：{}", this.root, e.getMessage());
        }
    }

    @Override
    public StoredFile store(String originalName, InputStream in, long size) {
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

    private long parseSize(String value) {
        if (value == null || value.isBlank()) {
            return Long.MAX_VALUE;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        long mul = 1;
        if (v.endsWith("KB")) {
            mul = 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("MB")) {
            mul = 1024 * 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("GB")) {
            mul = 1024L * 1024 * 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("B")) {
            v = v.substring(0, v.length() - 1);
        }
        try {
            return Long.parseLong(v.trim()) * mul;
        } catch (NumberFormatException e) {
            return 50L * 1024 * 1024;
        }
    }
}
