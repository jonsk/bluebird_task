-- BlueBird Task · SQLite 初始化 schema（V1__init.sql）
-- 唯一权威源：Task/02后端模块详细设计.md 各模块「数据表」小节（ADR-004）
-- 类型映射（ADR-016）：BIGINT→INTEGER、VARCHAR(n)→TEXT、SMALLINT/BOOLEAN→INTEGER、
--   TIMESTAMPTZ→TEXT(ISO-8601)、JSONB→TEXT(JSON)；时间列默认 strftime。
-- 逻辑删除：仅主实体表（sys_user / task / category / sys_department）含 deleted 列。

-- ============ identity ============
CREATE TABLE sys_user (
    id                   INTEGER PRIMARY KEY,
    username             TEXT    NOT NULL UNIQUE,
    external_id          TEXT    UNIQUE,
    password             TEXT,
    name                 TEXT    NOT NULL,
    mobile               TEXT,
    email                TEXT,
    avatar_url           TEXT,
    dept_id              INTEGER,
    role_code            TEXT    NOT NULL DEFAULT 'COMMON',
    status               TEXT    NOT NULL DEFAULT 'ACTIVE',
    must_change_password INTEGER NOT NULL DEFAULT 0,
    deleted              INTEGER NOT NULL DEFAULT 0,
    created_at           TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by           INTEGER,
    updated_at           TEXT,
    updated_by           INTEGER
);
CREATE INDEX idx_user_dept ON sys_user(dept_id) WHERE deleted=0;

CREATE TABLE sys_refresh_token (
    id           INTEGER PRIMARY KEY,
    user_id      INTEGER NOT NULL,
    token_hash   TEXT    NOT NULL UNIQUE,
    issued_at    TEXT    NOT NULL,
    expires_at   TEXT    NOT NULL,
    revoked      INTEGER NOT NULL DEFAULT 0,
    last_seen_at TEXT
);
-- 单活跃会话：同一用户仅一个未撤销 refresh
CREATE UNIQUE INDEX uq_refresh_user_active ON sys_refresh_token(user_id) WHERE revoked = 0;

-- ============ org ============
CREATE TABLE sys_department (
    id             INTEGER PRIMARY KEY,
    parent_id      INTEGER,
    name           TEXT NOT NULL,
    sort           INTEGER,
    corp_id        TEXT,
    leader_user_id INTEGER,
    deleted        INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at     TEXT
);
CREATE INDEX idx_dept_parent ON sys_department(parent_id) WHERE deleted=0;
CREATE INDEX idx_dept_leader ON sys_department(leader_user_id) WHERE deleted=0;

CREATE TABLE sys_user_department (
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER NOT NULL,
    dept_id    INTEGER NOT NULL,
    is_primary INTEGER NOT NULL DEFAULT 0,
    UNIQUE (user_id, dept_id)
);
CREATE INDEX idx_ud_user ON sys_user_department(user_id);
CREATE INDEX idx_ud_dept ON sys_user_department(dept_id);

-- ============ task ============
CREATE TABLE task (
    id                   INTEGER PRIMARY KEY,
    parent_id            INTEGER,
    title                TEXT NOT NULL,
    content              TEXT,
    status               TEXT NOT NULL DEFAULT 'ACTIVE',
    completed            INTEGER NOT NULL DEFAULT 0,
    priority             TEXT NOT NULL DEFAULT 'MEDIUM',
    due_at               TEXT,
    remind_at            TEXT,
    cycle_rule           TEXT,
    cycle_last_completed TEXT,
    category_id          INTEGER,
    owner_id             INTEGER NOT NULL,
    version              INTEGER NOT NULL DEFAULT 0,
    deleted              INTEGER NOT NULL DEFAULT 0,
    created_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by           INTEGER,
    updated_at           TEXT,
    updated_by           INTEGER
);
CREATE INDEX idx_task_owner  ON task(owner_id) WHERE deleted=0;
CREATE INDEX idx_task_parent ON task(parent_id);
CREATE INDEX idx_task_due    ON task(due_at)  WHERE deleted=0;
CREATE INDEX idx_task_status ON task(status, completed) WHERE deleted=0;

CREATE TABLE task_participant (
    id      INTEGER PRIMARY KEY,
    task_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    role    TEXT NOT NULL,
    UNIQUE (task_id, user_id, role)
);
CREATE INDEX idx_part_user ON task_participant(user_id, role);

CREATE TABLE category (
    id         INTEGER PRIMARY KEY,
    parent_id  INTEGER,
    name       TEXT NOT NULL,
    scope      TEXT NOT NULL DEFAULT 'PERSONAL',
    owner_id   INTEGER NOT NULL,
    dept_id    INTEGER,
    sort       INTEGER,
    version    INTEGER NOT NULL DEFAULT 0,
    deleted    INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX idx_cat_owner ON category(owner_id);
CREATE INDEX idx_cat_scope ON category(scope, dept_id) WHERE deleted=0;

CREATE TABLE tag (
    id       INTEGER PRIMARY KEY,
    name     TEXT NOT NULL,
    owner_id INTEGER NOT NULL,
    UNIQUE (owner_id, name)
);
CREATE TABLE task_tag (
    id      INTEGER PRIMARY KEY,
    task_id INTEGER NOT NULL,
    tag_id  INTEGER NOT NULL,
    UNIQUE (task_id, tag_id)
);

CREATE TABLE task_collect (
    id      INTEGER PRIMARY KEY,
    task_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    UNIQUE (task_id, user_id)
);

CREATE TABLE task_menu (
    id      INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL,
    name    TEXT NOT NULL,
    sort    INTEGER,
    version INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE task_menu_item (
    id      INTEGER PRIMARY KEY,
    menu_id INTEGER NOT NULL,
    task_id INTEGER NOT NULL,
    sort    INTEGER
);

-- ============ file ============
CREATE TABLE attachment (
    id            INTEGER PRIMARY KEY,
    task_id       INTEGER,
    file_name     TEXT NOT NULL,
    relative_path TEXT NOT NULL,
    size          INTEGER,
    md5           TEXT,
    created_by    INTEGER,
    created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX idx_att_task ON attachment(task_id);

-- ============ audit ============
CREATE TABLE audit_operate_log (
    id          INTEGER PRIMARY KEY,
    module      TEXT,
    action      TEXT,
    uri         TEXT,
    method      TEXT,
    req_params  TEXT,
    user_id     INTEGER,
    ip          TEXT,
    user_agent  TEXT,
    duration    INTEGER,
    status      INTEGER,
    msg         TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX idx_ol_user ON audit_operate_log(user_id);
CREATE INDEX idx_ol_time ON audit_operate_log(created_at);

CREATE TABLE audit_login_log (
    id          INTEGER PRIMARY KEY,
    username    TEXT,
    success     INTEGER,
    ip          TEXT,
    user_agent  TEXT,
    created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX idx_ll_user ON audit_login_log(username);
CREATE INDEX idx_ll_time ON audit_login_log(created_at);

-- ============ job ============
CREATE TABLE sys_job_log (
    id          INTEGER PRIMARY KEY,
    job         TEXT NOT NULL,
    status      INTEGER NOT NULL,
    started_at  TEXT NOT NULL,
    finished_at TEXT,
    duration    INTEGER,
    error       TEXT
);
CREATE INDEX idx_joblog_job_time ON sys_job_log(job, started_at);
