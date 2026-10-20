-- V5: Casdoor 接入（配置中心 + 组织同步日志 + 认证日志扩展）

-- ============ 配置中心（通用 KV） ============
-- Casdoor 连接参数、身份 provider、同步策略、安全/存储等运行期配置统一存本表；
-- 读取优先级：本表 > application.yml > 代码默认值（见 ConfigService）。
CREATE TABLE sys_config (
    id           INTEGER PRIMARY KEY,
    group_key    TEXT NOT NULL,
    config_key   TEXT NOT NULL,
    config_value TEXT,
    value_type   TEXT NOT NULL DEFAULT 'STRING',   -- STRING|INT|BOOL|SELECT|JSON
    encrypted    INTEGER NOT NULL DEFAULT 0,       -- 1 = AES-GCM 密文
    description  TEXT,
    updated_by   INTEGER,
    updated_at   TEXT,
    UNIQUE (group_key, config_key)
);
CREATE INDEX idx_cfg_group ON sys_config(group_key);

-- ============ 组织同步日志 ============
-- 每次同步一条（含定时与手动），记录统计；区别于 sys_job_log（仅手动重跑写入、无统计字段）。
CREATE TABLE org_sync_log (
    id            INTEGER PRIMARY KEY,
    source        TEXT NOT NULL,        -- CASDOOR | ...
    sync_type     TEXT NOT NULL,        -- FULL | USER | DEPT
    status        TEXT NOT NULL,        -- RUNNING | SUCCESS | FAILED
    started_at    TEXT NOT NULL,
    finished_at   TEXT,
    total         INTEGER,
    inserted      INTEGER,
    updated       INTEGER,
    skipped       INTEGER,
    error_message TEXT
);
CREATE INDEX idx_orgsync_time ON org_sync_log(started_at);

-- ============ 认证日志扩展 ============
-- provider 区分本地账密与 Casdoor；event_type 区分登录/退出（此前无退出日志）。
ALTER TABLE audit_login_log ADD COLUMN provider TEXT;
ALTER TABLE audit_login_log ADD COLUMN event_type TEXT DEFAULT 'LOGIN';
ALTER TABLE audit_login_log ADD COLUMN external_id TEXT;
CREATE INDEX idx_ll_provider ON audit_login_log(provider, event_type);

-- ============ 外部来源标记 ============
-- external_id 已存在（SysUser.externalId / Department.corpId），此处只补来源，便于区分本地 admin。
ALTER TABLE sys_user ADD COLUMN external_source TEXT;
ALTER TABLE sys_department ADD COLUMN external_source TEXT;
