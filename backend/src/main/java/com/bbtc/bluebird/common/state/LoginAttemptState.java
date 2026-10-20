package com.bbtc.bluebird.common.state;

import com.bbtc.bluebird.modules.config.application.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内登录失败计数 / 锁定（替代 Redis，ADR-016；02 §2.7）。
 *
 * <p>临时状态，进程重启即清零（单实例可接受）。阈值与锁定时长取自配置中心
 * （{@code security.*}），后台改完即时生效，无需重启。
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptState {

    private record Attempt(int failures, long lockedUntil) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final ConfigService config;

    /** 是否处于锁定态。 */
    public boolean isLocked(String key) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        if (a.lockedUntil() > 0 && a.lockedUntil() < System.currentTimeMillis()) {
            attempts.remove(key);
            return false;
        }
        return a.lockedUntil() > 0;
    }

    /** 记录一次失败；达阈值则锁定。 */
    public void recordFailure(String key) {
        int maxFail = config.getInt("security", "login-max-fail", 5);
        long lockSeconds = config.getInt("security", "login-lock-seconds", 900);
        attempts.compute(key, (k, a) -> {
            int failures = (a == null ? 0 : a.failures()) + 1;
            long lockedUntil = 0L;
            if (failures >= maxFail) {
                lockedUntil = System.currentTimeMillis() + lockSeconds * 1000L;
            }
            return new Attempt(failures, lockedUntil);
        });
    }

    /** 登录成功：清零。 */
    public void clear(String key) {
        attempts.remove(key);
    }
}
