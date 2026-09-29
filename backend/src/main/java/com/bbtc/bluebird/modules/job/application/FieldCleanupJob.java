package com.bbtc.bluebird.modules.job.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

/**
 * 孤儿文件清扫（02 §5.6，0306 Q6）：扫描磁盘上不存在于 attachment 的文件并删除。
 */
@Slf4j
@Component
public class FieldCleanupJob {

    private final AttachmentMapper attachmentMapper;
    private final Path root;

    public FieldCleanupJob(AttachmentMapper attachmentMapper,
                           @Value("${app.storage.root:./data/files}") String root) {
        this.attachmentMapper = attachmentMapper;
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Scheduled(cron = "${app.job.cleanup-cron:0 30 2 * * *}", zone = "Asia/Shanghai")
    public void cleanup() {
        try {
            int removed = run();
            log.info("孤儿文件清扫完成，删除 {} 个文件", removed);
        } catch (Exception e) {
            log.warn("孤儿文件清扫失败：{}", e.getMessage());
        }
    }

    /** 返回删除的孤儿文件数（幂等）。 */
    public int run() throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Set<String> known = new HashSet<>();
        for (Attachment a : attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery())) {
            known.add(a.getRelativePath().replace('\\', '/'));
        }
        int[] removed = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (!rel.endsWith(".tmp") && !known.contains(rel)) {
                    Files.deleteIfExists(file);
                    removed[0]++;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return removed[0];
    }
}
