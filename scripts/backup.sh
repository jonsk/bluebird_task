#!/usr/bin/env bash
# SQLite 备份样例（ADR-016）。部署方按需改造并纳入定时任务/恢复演练。
# 用法：scripts/backup.sh [DB_PATH] [BACKUP_DIR]
# 说明：优先 `sqlite3 .backup`（在线一致快照，WAL 安全）；无 sqlite3 时回退到「停写复制 .db」。
set -euo pipefail

DB_PATH="${1:-./data/bluebird.db}"
BACKUP_DIR="${2:-./backup}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/bluebird-$STAMP.db"

if command -v sqlite3 >/dev/null 2>&1; then
  sqlite3 "$DB_PATH" ".backup '$OUT'"
else
  echo "!! 未找到 sqlite3；改为复制 .db（请先停止应用写入，避免不一致）" >&2
  cp "$DB_PATH" "$OUT"
fi

echo ">> 数据库备份完成：$OUT"
echo ">> 附件目录请另行备份（默认 /data/bluebird/files）"
