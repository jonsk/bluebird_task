#!/usr/bin/env bash
# M0 占位：本地起依赖 + （后续）不在容器内起后端则本地跑 jar。
# 用法：scripts/dev-up.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo ">> 起本地依赖（PostgreSQL + Redis）"
docker compose -f "$ROOT/deploy/docker-compose.dev.yml" up -d

echo ">> 依赖就绪（bluebird-pg:5432 / bluebird-redis:6379）"
echo ">> 后续：后端 mvn 起跑、前端 pnpm dev 联调（见 01 §11）"
