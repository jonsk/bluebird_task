-- V14: 保存 Casdoor 返回的 id_token，用于静默登出（RP-initiated logout）
--
-- 不带 id_token_hint 时 Casdoor 会弹「确认退出」页，用户点退出后无法直达登录页。
-- 每个用户只保留最近一次登录的 id_token（同一浏览器共享 Casdoor SSO 会话）。
CREATE TABLE sys_oidc_session (
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER NOT NULL,
    id_token   TEXT    NOT NULL,
    updated_at TEXT,
    UNIQUE (user_id)
);
