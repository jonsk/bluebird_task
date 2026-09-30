package com.bbtc.bluebird.config.mybatis;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JS 安全的 53 位主键生成器（雪花变体）。
 *
 * <h2>为什么不能用 MyBatis-Plus 默认雪花 ID</h2>
 * MP 默认 {@code DefaultIdentifierGenerator} 产出的 id 约 <b>2.1e18</b>，超过 JavaScript 的
 * {@code Number.MAX_SAFE_INTEGER}（2^53-1 ≈ 9.007e15）。浏览器 {@code JSON.parse} 会<b>静默改写末位</b>，
 * 实测：服务端 {@code 2105180185270796289} → 浏览器 {@code 2105180185270796300}。
 * 后果是「按 id 访问」的请求全部落空（实测任务详情返回 10004 → 详情面板打不开、无法编辑/完成/删除，
 * 子任务与附件操作一并失效）。前端大量使用 {@code Number(task.id)}，无法在客户端补救。
 *
 * <h2>本实现</h2>
 * <pre>
 * id = ((当前毫秒 - 纪元) &lt;&lt; 12) | 毫秒内序列
 * </pre>
 * 41 位毫秒 + 12 位序列 = <b>恰好 53 位</b>，最大值 = 2^53-1 = {@code Number.MAX_SAFE_INTEGER}，
 * 即前端可精确表示的整数上限，前后端都不会丢精度。纪元 2024-01-01 起可用约 69 年（至 ~2093）。
 *
 * <p>单实例假设：本布局不保留 workerId。ADR-016 已明确 SQLite 仅支持单实例，
 * 多实例（集群化）本就要求更换为 C/S 数据库并重新设计 id/锁方案，届时一并处理。
 *
 * <p>时钟回拨兜底（对齐 02 §1.10 R7 与 {@code common.util.IdGenerator}）：回拨 ≤5s 自旋等待追平，
 * 超出则 fail-fast，避免生成重复 id。
 */
@Slf4j
@Component
public class JsSafeIdGenerator implements IdentifierGenerator {

    /** 自定义纪元 2024-01-01T00:00:00Z（毫秒）。 */
    private static final long EPOCH = 1704067200000L;

    /** 毫秒内序列位数。 */
    private static final int SEQUENCE_BITS = 12;

    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    /** 毫秒时间戳位数（41 位），与 12 位序列合计 53 位。 */
    private static final long MAX_MILLIS = (1L << 41) - 1;

    /** 允许的最大时钟回拨等待（毫秒）。 */
    private static final long MAX_BACKWARD_WAIT_MS = 5000L;

    private long lastMillis = -1L;
    private long sequence = 0L;

    @Override
    public synchronized Number nextId(Object entity) {
        long now = System.currentTimeMillis();
        if (now < lastMillis) {
            long offset = lastMillis - now;
            if (offset > MAX_BACKWARD_WAIT_MS) {
                throw new IllegalStateException("Clock moved backwards beyond threshold: " + offset + "ms");
            }
            while ((now = System.currentTimeMillis()) < lastMillis) {
                Thread.onSpinWait();
            }
        }
        if (now == lastMillis) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 该毫秒序列耗尽（4096/ms）→ 顺延到下一毫秒
                now = waitNextMillis(lastMillis);
            }
        } else {
            sequence = 0L;
        }
        lastMillis = now;

        long millis = now - EPOCH;
        if (millis < 0) {
            throw new IllegalStateException("System clock is before the configured epoch (2024-01-01Z)");
        }
        if (millis > MAX_MILLIS) {
            throw new IllegalStateException("53-bit id space exhausted (41-bit millis overflow)");
        }
        return (millis << SEQUENCE_BITS) | sequence;
    }

    private long waitNextMillis(long lastMs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastMs) {
            Thread.onSpinWait();
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
