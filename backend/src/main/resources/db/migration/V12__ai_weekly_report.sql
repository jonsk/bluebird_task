-- AI 模型配置与个人周报（2026-11-10）
-- 模型可配置多条，但全局最多一条 is_active=1（SQLite 部分唯一索引兜底）。

CREATE TABLE ai_model_config (
    id              INTEGER PRIMARY KEY,
    name            TEXT    NOT NULL,
    provider        TEXT    NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
    base_url        TEXT    NOT NULL,
    api_key_cipher  TEXT,
    model           TEXT    NOT NULL,
    temperature     REAL    NOT NULL DEFAULT 0.3,
    max_tokens      INTEGER NOT NULL DEFAULT 2048,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,
    enabled         INTEGER NOT NULL DEFAULT 1,
    active          INTEGER NOT NULL DEFAULT 0,
    version         INTEGER NOT NULL DEFAULT 0,
    deleted         INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by      INTEGER,
    updated_at      TEXT,
    updated_by      INTEGER
);

CREATE UNIQUE INDEX uq_ai_model_active ON ai_model_config(active)
    WHERE active = 1 AND deleted = 0;

CREATE TABLE weekly_report (
    id                  INTEGER PRIMARY KEY,
    owner_id            INTEGER NOT NULL,
    week_start          TEXT    NOT NULL,
    week_end            TEXT    NOT NULL,
    status              TEXT    NOT NULL DEFAULT 'DRAFT',
    content_html        TEXT,
    ai_result_json      TEXT,
    source_note_ids     TEXT,
    source_note_versions TEXT,
    model_config_id     INTEGER,
    model_name          TEXT,
    prompt_version      TEXT,
    user_prompt_snapshot TEXT,
    input_tokens        INTEGER NOT NULL DEFAULT 0,
    output_tokens       INTEGER NOT NULL DEFAULT 0,
    error_message       TEXT,
    generated_at        TEXT,
    completed_at        TEXT,
    version             INTEGER NOT NULL DEFAULT 0,
    deleted             INTEGER NOT NULL DEFAULT 0,
    created_at          TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by          INTEGER,
    updated_at          TEXT,
    updated_by          INTEGER
);

CREATE UNIQUE INDEX uq_weekly_report_owner_week ON weekly_report(owner_id, week_start)
    WHERE deleted = 0;
CREATE INDEX idx_weekly_report_owner_list ON weekly_report(owner_id, week_start DESC)
    WHERE deleted = 0;

CREATE TABLE weekly_report_preference (
    id           INTEGER PRIMARY KEY,
    user_id      INTEGER NOT NULL,
    style        TEXT    NOT NULL DEFAULT 'concise',
    focus        TEXT,
    custom_rules TEXT,
    created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    created_by   INTEGER,
    updated_at   TEXT,
    updated_by   INTEGER
);

CREATE UNIQUE INDEX uq_weekly_report_preference_user ON weekly_report_preference(user_id);
