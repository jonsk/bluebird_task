package com.bbtc.bluebird.common.model;

import java.util.List;

/**
 * 登录用户上下文（02 §1.7）。
 */
public record LoginUser(Long id, String username, String name, Long deptId,
                        String roleCode, List<String> authorities) {
}
