#!/usr/bin/env bash
# 一键部署：git pull -> 构建（前端 + 后端单制品 jar）-> 安装 -> 重启 -> 健康检查（失败自动回滚）。
#
# 默认零参数即可：
#   ./deploy/deploy.sh
# 默认值：
#   代码目录   = 本脚本所在仓库根（自动识别）
#   分支       = main
#   运行目录   = <仓库根>/runtime（application.yml、data/、logs/、releases/ 都在这里）
#   端口       = 8080（SERVER_PORT 可覆盖）
#   服务名     = bluebird（有该 systemd unit 就用它，否则 pidfile + nohup）
#
# 常用覆盖：
#   --dir /opt/bluebird     换运行目录
#   --no-pull               不拉代码，直接用当前工作区构建
#   --no-build              跳过构建，装 --jar 指定的包（或上次构建产物）
#   --jar /path/x.jar       直接安装已有 jar（隐含 --no-build）
#   --branch dev            拉指定分支
#   --no-systemd            强制 pidfile 方式
#   --keep 5                历史包保留个数
#   --dry-run               只打印将执行的动作
#
# 服务器依赖：git、JDK 21、Maven、Node 20+、pnpm（corepack enable）。
# 运行目录不会被覆盖：application.yml 与 data/ 始终保留。
set -Eeuo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${BRANCH:-main}"
APP_DIR="${APP_DIR:-$REPO_DIR/runtime}"
JAR_NAME="bluebird-task.jar"
PORT="${SERVER_PORT:-8080}"
SERVICE="${BLUEBIRD_SERVICE:-bluebird}"
KEEP="${KEEP_RELEASES:-5}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"
JAVA_OPTS="${JAVA_OPTS:--Xmx512m}"

PULL=1
BUILD=1
USE_SYSTEMD=1
DRY_RUN=0
NEW_JAR=""

usage() {
  sed -n '2,24p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar) NEW_JAR="${2:?--jar 需要路径}"; BUILD=0; shift 2 ;;
    --branch) BRANCH="${2:?--branch 需要分支名}"; shift 2 ;;
    --dir) APP_DIR="${2:?--dir 需要路径}"; shift 2 ;;
    --port) PORT="${2:?--port 需要端口}"; shift 2 ;;
    --service) SERVICE="${2:?--service 需要服务名}"; shift 2 ;;
    --keep) KEEP="${2:?--keep 需要数字}"; shift 2 ;;
    --no-pull) PULL=0; shift ;;
    --no-build) BUILD=0; shift ;;
    --no-systemd) USE_SYSTEMD=0; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 2 ;;
  esac
done

die() { echo "!! $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }
run() { if [[ "$DRY_RUN" -eq 1 ]]; then echo "   [dry-run] $*"; else "$@"; fi; }

TARGET_JAR="$APP_DIR/$JAR_NAME"
RELEASES="$APP_DIR/releases"
LOG_DIR="$APP_DIR/logs"
PID_FILE="$APP_DIR/run/bluebird.pid"
HEALTH_URL="http://127.0.0.1:$PORT/actuator/health"
STAMP="$(date +%Y%m%d-%H%M%S)"

# ---------------------------------------------------------------- 环境检查

step "环境检查"
command -v java >/dev/null 2>&1 || die "缺少 java（需要 21+）"
java_version="$(java -version 2>&1 | head -1)"
java_major="$(sed -n 's/.*version "\([0-9]*\).*/\1/p' <<<"$java_version" | head -1)"
[[ -n "$java_major" && "$java_major" -ge 21 ]] || die "java 需要 >= 21，当前 $java_version"

if [[ "$PULL" -eq 1 ]]; then
  command -v git >/dev/null 2>&1 || die "缺少 git（或用 --no-pull）"
  git -C "$REPO_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || die "$REPO_DIR 不是 git 仓库（或用 --no-pull）"
fi

if [[ "$BUILD" -eq 1 ]]; then
  for c in node pnpm mvn; do
    command -v "$c" >/dev/null 2>&1 || die "缺少 $c（构建需要；或用 --no-build 直接装包）"
  done
  node_major="$(node -p 'process.versions.node.split(".")[0]')"
  [[ "$node_major" -ge 20 ]] || die "node 需要 >= 20，当前 $(node -v)"
fi
echo "   java=$java_version  repo=$REPO_DIR  app=$APP_DIR  port=$PORT"

# ---------------------------------------------------------------- 拉取代码

if [[ "$PULL" -eq 1 ]]; then
  step "拉取代码（origin/$BRANCH）"
  dirty="$(git -C "$REPO_DIR" status --porcelain --untracked-files=no)"
  [[ -z "$dirty" ]] || die "工作区有未提交改动，已中止（先提交/还原，或用 --no-pull）：
$dirty"

  before="$(git -C "$REPO_DIR" rev-parse HEAD)"
  run git -C "$REPO_DIR" fetch --prune origin "$BRANCH"
  run git -C "$REPO_DIR" checkout "$BRANCH"
  run git -C "$REPO_DIR" pull --ff-only origin "$BRANCH"
  after="$(git -C "$REPO_DIR" rev-parse HEAD 2>/dev/null || echo "$before")"
  if [[ "$DRY_RUN" -eq 0 ]]; then
    echo "   $before -> $after"
    # 脚本自身被这次 pull 更新过：重新加载后再继续，避免执行到半旧半新的逻辑
    if [[ "${BB_DEPLOY_RELOADED:-0}" != "1" ]] \
      && ! git -C "$REPO_DIR" diff --quiet "$before" "$after" -- deploy/deploy.sh; then
      echo "   deploy.sh 有更新，重新加载脚本"
      reload_args=(--no-pull)
      if (( BUILD == 0 )); then reload_args+=(--no-build); fi
      if (( USE_SYSTEMD == 0 )); then reload_args+=(--no-systemd); fi
      if (( DRY_RUN == 1 )); then reload_args+=(--dry-run); fi
      if [[ -n "$NEW_JAR" ]]; then reload_args+=(--jar "$NEW_JAR"); fi
      BB_DEPLOY_RELOADED=1 \
        APP_DIR="$APP_DIR" BRANCH="$BRANCH" SERVER_PORT="$PORT" \
        BLUEBIRD_SERVICE="$SERVICE" KEEP_RELEASES="$KEEP" \
        exec bash "$REPO_DIR/deploy/deploy.sh" "${reload_args[@]}"
    fi
  fi
else
  step "跳过拉取代码"
fi

# ---------------------------------------------------------------- 构建

if [[ "$BUILD" -eq 1 ]]; then
  step "构建单制品 jar"
  JAR_OUT="$REPO_DIR/deploy/out/bluebird-task.jar"
  run bash "$REPO_DIR/deploy/package.sh" --out "$JAR_OUT"
  NEW_JAR="$JAR_OUT"
else
  step "跳过构建"
  [[ -n "$NEW_JAR" ]] || NEW_JAR="$REPO_DIR/deploy/out/bluebird-task.jar"
fi

[[ -f "$NEW_JAR" ]] || die "找不到待部署的 jar：$NEW_JAR"
if command -v unzip >/dev/null 2>&1; then
  unzip -l "$NEW_JAR" | grep -q 'BOOT-INF/classes/' || die "$NEW_JAR 不是 Spring Boot 可执行 jar"
fi

# ---------------------------------------------------------------- 安装

systemd_active() {
  [[ "$USE_SYSTEMD" -eq 1 ]] && command -v systemctl >/dev/null 2>&1 \
    && systemctl cat "$SERVICE" >/dev/null 2>&1
}

stop_app() {
  if systemd_active; then
    run systemctl stop "$SERVICE"
    return
  fi
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid="$(cat "$PID_FILE")"
    if kill -0 "$pid" 2>/dev/null; then
      run kill "$pid"
      for _ in $(seq 1 30); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then run kill -9 "$pid"; fi
    fi
    run rm -f "$PID_FILE"
  fi
}

start_app() {
  if systemd_active; then
    run systemctl start "$SERVICE"
    return
  fi
  run mkdir -p "$LOG_DIR" "$(dirname "$PID_FILE")"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "   [dry-run] (cd $APP_DIR && nohup java $JAVA_OPTS -jar $TARGET_JAR >> $LOG_DIR/app.log 2>&1 & echo \$! > $PID_FILE)"
    return
  fi
  ( cd "$APP_DIR" && nohup java $JAVA_OPTS -jar "$TARGET_JAR" >>"$LOG_DIR/app.log" 2>&1 & echo $! >"$PID_FILE" )
}

wait_healthy() {
  step "健康检查（最多 ${HEALTH_TIMEOUT}s）：$HEALTH_URL"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "   [dry-run] 轮询 $HEALTH_URL"
    return 0
  fi
  local waited=0
  while (( waited < HEALTH_TIMEOUT )); do
    if body="$(curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null)" && grep -q '"status":"UP"' <<<"$body"; then
      echo "   服务已就绪（${waited}s）"
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  return 1
}

step "部署 $NEW_JAR -> $TARGET_JAR"
run mkdir -p "$APP_DIR" "$RELEASES"

if [[ -f "$TARGET_JAR" ]]; then
  step "备份当前包 -> $RELEASES/bluebird-task-$STAMP.jar"
  run cp -f "$TARGET_JAR" "$RELEASES/bluebird-task-$STAMP.jar"
fi
DB="$APP_DIR/data/bluebird.db"
if [[ -f "$DB" ]] && command -v sqlite3 >/dev/null 2>&1; then
  step "备份 SQLite -> $RELEASES/bluebird-$STAMP.db"
  run sqlite3 "$DB" ".backup '$RELEASES/bluebird-$STAMP.db'"
fi

step "停止服务"
stop_app

step "安装新包"
run cp -f "$NEW_JAR" "$TARGET_JAR.new"
run mv -f "$TARGET_JAR.new" "$TARGET_JAR"

step "启动服务"
start_app

if ! wait_healthy; then
  echo "!! 健康检查失败，开始回滚" >&2
  if [[ "$DRY_RUN" -eq 0 ]]; then
    echo "---- 最近日志 ----" >&2
    tail -n 40 "$LOG_DIR/app.log" 2>/dev/null >&2 || true
  fi
  stop_app
  previous="$(ls -1t "$RELEASES"/bluebird-task-*.jar 2>/dev/null | head -1 || true)"
  if [[ -n "$previous" ]]; then
    echo "   回滚到 $previous"
    run cp -f "$previous" "$TARGET_JAR"
    start_app
    wait_healthy || die "回滚后仍不健康，请查看 $LOG_DIR/app.log 或 journalctl -u $SERVICE"
    die "部署失败，已回滚到 $previous"
  fi
  die "部署失败且没有可回滚的历史包，请查看 $LOG_DIR/app.log 或 journalctl -u $SERVICE"
fi

step "清理历史包（保留最近 $KEEP 个）"
if [[ "$DRY_RUN" -eq 0 ]]; then
  ls -1t "$RELEASES"/bluebird-task-*.jar 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
    echo "   删除 $old"
    rm -f "$old"
  done
fi

step "完成"
echo "   包：$TARGET_JAR"
echo "   端口：$PORT"
echo "   健康：$(curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null || echo '<未响应>')"
