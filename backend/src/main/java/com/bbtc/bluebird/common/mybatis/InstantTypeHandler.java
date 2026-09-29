package com.bbtc.bluebird.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * {@link Instant} ↔ SQLite TEXT（ISO-8601，UTC）TypeHandler（ADR-016，02 §1.10）。
 *
 * <p>SQLite 无原生时间类型；JDBC 默认会把 Instant 绑成 epoch 毫秒整数、读时又用
 * {@code getTimestamp} 解析文本，导致「Error parsing time stamp」。此处理器统一以
 * ISO-8601 文本存取（如 {@code 2026-09-29T09:28:54.123Z}），与 DDL 的
 * {@code strftime('%Y-%m-%dT%H:%M:%fZ','now')} 默认值一致。
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.toString());
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Instant parse(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            // 兜底：SQLite 常见空格分隔格式，补 'Z'
            try {
                return Instant.parse(v.replace(' ', 'T') + (v.endsWith("Z") ? "" : "Z"));
            } catch (DateTimeParseException ex) {
                // 兼容历史数值型（epoch 毫秒）
                try {
                    return Instant.ofEpochMilli(Long.parseLong(v));
                } catch (NumberFormatException nfe) {
                    throw new SQLException("无法解析时间: " + value, ex);
                }
            }
        }
    }
}
