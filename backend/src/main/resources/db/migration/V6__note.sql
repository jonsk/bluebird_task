-- BlueBird Task · Note（工作日志）模块（V6__note.sql）
-- 5 张表 + 1 视图 + tag(name) 索引。全新部署，只建结构（ADR-016 类型映射）。

-- ============ note（工作日志） ============
CREATE TABLE note (
    id          INTEGER PRIMARY KEY,
    content     TEXT    NOT NULL,                   -- 纯文本，上限 20000（Service 校验）
    log_date    TEXT    NOT NULL,                   -- 工作日 YYYY-MM-DD
    visibility  TEXT    NOT NULL DEFAULT 'PRIVATE', -- PRIVATE | INTERNAL | PUBLIC
    pinned      INTEGER NOT NULL DEFAULT 0,
    archived    INTEGER NOT NULL DEFAULT 0,
    owner_id    INTEGER NOT NULL,
    dept_id     INTEGER,                            -- owner 部门快照（INTERNAL 用）
    version     INTEGER NOT NULL DEFAULT 0,         -- 乐观锁（实体 @Version）
    deleted     INTEGER NOT NULL DEFAULT 0,         -- 逻辑删除，不可恢复
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by  INTEGER,
    updated_at  TEXT,
    updated_by  INTEGER
);
CREATE INDEX idx_note_owner_list ON note(owner_id, pinned DESC, log_date DESC, created_at DESC) WHERE deleted=0;
CREATE INDEX idx_note_explore    ON note(visibility, pinned DESC, log_date DESC, created_at DESC) WHERE deleted=0 AND archived=0;
CREATE INDEX idx_note_internal   ON note(dept_id, pinned DESC, log_date DESC, created_at DESC) WHERE deleted=0 AND archived=0;
CREATE INDEX idx_note_logdate    ON note(owner_id, log_date) WHERE deleted=0 AND archived=0;

-- ============ note_tag ============
CREATE TABLE note_tag (
    id      INTEGER PRIMARY KEY,
    note_id INTEGER NOT NULL,
    tag_id  INTEGER NOT NULL,
    UNIQUE (note_id, tag_id)
);
CREATE INDEX idx_nt_note ON note_tag(note_id);
CREATE INDEX idx_nt_tag  ON note_tag(tag_id);

-- ============ note_user_grant（用户级全量授权） ============
CREATE TABLE note_user_grant (
    id          INTEGER PRIMARY KEY,
    grantor_id  INTEGER NOT NULL,               -- 授权人（日志 owner）
    grantee_id  INTEGER NOT NULL,               -- 被授权人
    role        TEXT    NOT NULL,               -- VIEWER | EDITOR
    status      TEXT    NOT NULL DEFAULT 'PENDING', -- PENDING|APPROVED|REJECTED|REVOKED
    mode        TEXT    NOT NULL DEFAULT 'OWNER',   -- OWNER | SUPERIOR | ADMIN
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by  INTEGER,
    approved_at TEXT,
    approved_by INTEGER,
    revoked_at  TEXT,
    revoked_by  INTEGER
);
CREATE UNIQUE INDEX uq_nug_active ON note_user_grant(grantor_id, grantee_id)
    WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX idx_nug_grantor ON note_user_grant(grantor_id, status);
CREATE INDEX idx_nug_grantee ON note_user_grant(grantee_id, status);
CREATE INDEX idx_nug_created ON note_user_grant(created_by, status);

-- ============ note_attachment_ref ============
CREATE TABLE note_attachment_ref (
    id            INTEGER PRIMARY KEY,
    note_id       INTEGER NOT NULL,
    attachment_id INTEGER NOT NULL,
    sort          INTEGER,
    UNIQUE (note_id, attachment_id)
);
CREATE INDEX idx_nar_note ON note_attachment_ref(note_id);
CREATE INDEX idx_nar_att  ON note_attachment_ref(attachment_id);

-- ============ note_relation（仅 REFERENCE） ============
CREATE TABLE note_relation (
    id              INTEGER PRIMARY KEY,
    source_note_id  INTEGER NOT NULL,
    target_type     TEXT    NOT NULL DEFAULT 'NOTE', -- NOTE | TASK
    target_id       INTEGER NOT NULL,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by      INTEGER,
    UNIQUE (source_note_id, target_type, target_id)
);
CREATE INDEX idx_nr_source ON note_relation(source_note_id);
CREATE INDEX idx_nr_target ON note_relation(target_type, target_id);

-- ============ tag(name) 索引 ============
CREATE INDEX idx_tag_name ON tag(name);

-- ============ v_tag_relation（跨实体标签统计视图） ============
-- 注意：本视图只做实体存在性过滤（deleted=0），**不含归档/权限过滤**。
-- Service 若用本视图做标签计数，必须按当前用户可见范围二次过滤，否则会泄露越权计数。
CREATE VIEW v_tag_relation AS
SELECT 'TASK' AS target_type, tt.task_id AS target_id, tt.tag_id
FROM task_tag tt
JOIN task t ON t.id = tt.task_id
WHERE t.deleted = 0
UNION ALL
SELECT 'NOTE', nt.note_id, nt.tag_id
FROM note_tag nt
JOIN note n ON n.id = nt.note_id
WHERE n.deleted = 0;
