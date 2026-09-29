package com.bbtc.bluebird.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite PRAGMA 初始化（ADR-016，02 §1.9）。
 *
 * <p>连接池 max-pool-size=1，故单物理连接即承载全部 PRAGMA：
 * journal_mode=WAL（库级持久）、busy_timeout=5000、foreign_keys=ON、synchronous=NORMAL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlitePragmaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    private static final String[] PRAGMAS = {
            "PRAGMA journal_mode=WAL",
            "PRAGMA busy_timeout=5000",
            "PRAGMA foreign_keys=ON",
            "PRAGMA synchronous=NORMAL"
    };

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            for (String pragma : PRAGMAS) {
                try {
                    st.execute(pragma);
                } catch (SQLException e) {
                    log.warn("执行 {} 失败：{}", pragma, e.getMessage());
                }
            }
            log.info("SQLite PRAGMA 初始化完成（WAL / busy_timeout / foreign_keys / synchronous）");
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite PRAGMA 初始化失败", e);
        }
    }
}
