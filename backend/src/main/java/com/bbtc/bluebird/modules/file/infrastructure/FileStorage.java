package com.bbtc.bluebird.modules.file.infrastructure;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/** 存储抽象（02 §5.4）。 */
public interface FileStorage {

    StoredFile store(String originalName, InputStream in, long size);

    Resource load(String relativePath);

    void delete(String relativePath);

    /** 落盘结果。 */
    record StoredFile(String relativePath, String fileName, long size, String md5) {
    }
}
