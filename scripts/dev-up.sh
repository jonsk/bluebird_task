#!/usr/bin/env bash
# M0 占位：去 PG/Redis 后本地**无外部依赖**（SQLite 嵌入式、无缓存，ADR-016）。
# SQLite 随应用自建（默认 ./data/bluebird.db），无需 docker compose up。
# 用法：scripts/dev-up.sh（仅提示；实际直接起后端/前端）
set -euo pipefail

echo ">> 无外部依赖（SQLite 嵌入式、无缓存，ADR-016）；无需 docker compose up。"
echo ">> 起后端：cd backend && mvn spring-boot:run"
echo ">> 起前端：cd frontend && pnpm dev（见 01 §11）"
