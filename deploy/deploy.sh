#!/usr/bin/env bash
# 服务器端部署脚本：安装新 jar、重启服务、健康检查、失败自动回滚。
#
# 只做「已构建好的 jar」的安装与切换；构建请在本机执行 deploy/package.sh（需要 node/pnpm/mvn）。
#
# 目录约定（默认，可用参数覆盖）：
#   /opt/bluebird/bluebird-task.jar      当前运行包（原子替换）
#   /opt/bluebird/application.yml        运行时配置（首次启动由应用释放，勿覆盖）
#   /opt/bluebird/data/                  SQLite 与附件（勿删除）
#   /opt/bluebird/releases/              历史包（保留最近 N 个，用于回滚）
#   /opt/bluebird/logs/app.log           无 systemd 时的运行日志
#
# 用法：
#   deploy/deploy.sh --jar /tmp/bluebird-task.jar
#   deploy/deploy.sh --jar ./x.jar --dir /opt/bluebird --port 8080 --service bluebird
#   deploy/deploy.sh --jar ./x.jar --no-systemd     # 无 systemd 时用 pidfile + nohup
#   deploy/deploy.sh --jar ./x.jar --dry-run
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/bluebird}"
JAR_NAME="bluebird-task.jar"
PORT="${SERVER_PORT:-8080}"
SERVICE="${BLUEBIRD_SERVICE:-bluebird}"
KEEP="${KEEP_RELEASES:-5}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-90}"
JAVA_OPTS="${JAVA_OPTS:--Xmx512m}"
NEW_JAR=""
USE_SYSTEMD=1
DRY_RUN=0

usage() {
  sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar) NEW_JAR="${2:?--jar 需要路径}"; shift 2 ;;
    --dir) APP_DIR="${2:?--dir 需要路径}"; shift 2 ;;
    --port) PORT="${2:?--port 需要端口}"; shift 2 ;;
    --service) SERVICE="${2:?--service 需要服务名}"; shift 2 ;;
    --keep) KEEP="${2:?--keep 需要数字}"; shift 2 ;;
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

[[ -n "$NEW_JAR" ]] || { usage >&2; die "缺少 --jar"; }
[[ -f "$NEW_JAR" ]] || die "找不到 jar：$NEW_JAR"
command -v java >/dev/null 2>&1 || die "缺少 java（需要 21+）"

# 快速校验是不是 Spring Boot 可执行 jar（可选依赖 unzip）
if command -v unzip >/dev/null 2>&1; then
  unzip -l "$NEW_JAR" | grep -q 'BOOT-INF/classes/' || die "$NEW_JAR 不是 Spring Boot 可执行 jar"
fi

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
      kill -0 "$pid" 2>/dev/null && run kill -9 "$pid"
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

# 1) 备份当前包与 SQLite（有 sqlite3 时做在线一致备份）
if [[ -f "$TARGET_JAR" ]]; then
  step "备份当前包 -> $RELEASES/bluebird-task-$STAMP.jar"
  run cp -f "$TARGET_JAR" "$RELEASES/bluebird-task-$STAMP.jar"
fi
DB="$APP_DIR/data/bluebird.db"
if [[ -f "$DB" ]] && command -v sqlite3 >/dev/null 2>&1; then
  step "备份 SQLite -> $RELEASES/bluebird-$STAMP.db"
  run sqlite3 "$DB" ".backup '$RELEASES/bluebird-$STAMP.db'"
fi

# 2) 停服务
step "停止服务"
stop_app

# 3) 原子替换 jar（同分区 mv 不会出现半个文件）
step "安装新包"
run cp -f "$NEW_JAR" "$TARGET_JAR.new"
run mv -f "$TARGET_JAR.new" "$TARGET_JAR"

# 4) 启动 + 健康检查
step "启动服务"
start_app

if ! wait_healthy; then
  echo "!! 健康检查失败，开始回滚" >&2
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

# 5) 清理旧包
step "清理历史包（保留最近 $KEEP 个）"
if [[ "$DRY_RUN" -eq 0 ]]; then
  ls -1t "$RELEASES"/bluebird-task-*.jar 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
    echo "   删除 $old"
    rm -f "$old"
  done
fi

step "完成"
echo "   包：$TARGET_JAR"
echo "   端口：$PORT   健康：$(curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null || echo '<未响应>')"
