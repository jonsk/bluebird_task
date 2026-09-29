package com.bbtc.bluebird.common.model;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前用户上下文（02 §1.7）：从 {@code SecurityContextHolder} 取 {@link LoginUser}。
 */
public final class UserContext {

    private UserContext() {
    }

    public static LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu;
        }
        return null;
    }

    public static Long currentUserId() {
        LoginUser lu = current();
        return lu == null ? null : lu.id();
    }

    public static String currentRoleCode() {
        LoginUser lu = current();
        return lu == null ? null : lu.roleCode();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(currentRoleCode());
    }
}
