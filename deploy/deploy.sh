#!/usr/bin/env bash
# 简单部署：检查目录 -> 拉取代码 -> 构建 -> 拷贝 jar 到 bluebird-deploy
#
# 目录约定（不写死用户名，按脚本位置推导）：
#   代码目录 = 本脚本所在仓库根
#   部署目录 = 代码目录的同级 bluebird-deploy
#
# 用法：./deploy/deploy.sh
# 依赖：git、JDK 21、Maven、Node 20+、pnpm
# 不负责重启服务，拷完包后自行重启（如 sudo systemctl restart bluebird）。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null || echo "$SCRIPT_DIR")"
APP_DIR="$(dirname "$REPO_DIR")/bluebird-deploy"
BRANCH="${BRANCH:-main}"

JAR_SRC="$REPO_DIR/backend/target/bluebird-task.jar"
JAR_DST="$APP_DIR/bluebird-task.jar"

step() { printf '\n==> %s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 1 目录检查
step "检查目录"
[[ -d "$REPO_DIR" ]] || die "代码目录不存在：$REPO_DIR"
if [[ ! -d "$APP_DIR" ]]; then
  mkdir -p "$APP_DIR" || die "无法创建部署目录：$APP_DIR"
  echo "   已创建 $APP_DIR"
fi
[[ -w "$APP_DIR" ]] || die "没有写权限：$APP_DIR"
echo "   代码目录：$REPO_DIR"
echo "   部署目录：$APP_DIR"

# mvn 需要 JAVA_HOME，很多机器只有 PATH
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  bin="$(command -v java || true)"
  [[ -n "$bin" ]] || die "缺少 java（需要 21+）"
  bin="$(readlink -f "$bin" 2>/dev/null || echo "$bin")"
  export JAVA_HOME="$(cd "$(dirname "$bin")/.." && pwd)"
  echo "   JAVA_HOME=$JAVA_HOME"
fi

# ---------------------------------------------------------------- 2 拉取代码
step "拉取代码（origin/$BRANCH）"
git -C "$REPO_DIR" fetch origin "$BRANCH"
git -C "$REPO_DIR" pull --ff-only origin "$BRANCH"

# ---------------------------------------------------------------- 3 构建
step "构建前端"
pnpm -C "$REPO_DIR/frontend" install --frozen-lockfile
pnpm -C "$REPO_DIR/frontend" build

step "构建后端（单制品 jar，内嵌前端）"
mvn -B -f "$REPO_DIR/backend/pom.xml" -DskipTests package
[[ -f "$JAR_SRC" ]] || die "构建产物不存在：$JAR_SRC"

# ---------------------------------------------------------------- 4 拷贝 jar
step "拷贝 jar"
cp -f "$JAR_SRC" "$JAR_DST.new"
mv -f "$JAR_DST.new" "$JAR_DST"

echo "   完成：$JAR_DST"
ls -lh "$JAR_DST"
