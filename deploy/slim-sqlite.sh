#!/usr/bin/env bash
# Slim sqlite-jdbc native libraries for the target platform.
# Runs after spring-boot:repackage; writes the shippable jar into target/dist.
#
# Usage:
#   deploy/slim-sqlite.sh
#   deploy/slim-sqlite.sh --platform linux-musl --arch x86_64
#   deploy/slim-sqlite.sh --all          # 5 平台 x 2 架构，共 10 个包
#
# Env fallbacks (used by Maven): SLIM_JAR, SLIM_VERSION, SLIM_PLATFORM, SLIM_ARCH, SLIM_OUT, SLIM_ALL.

set -euo pipefail

JAR="${SLIM_JAR:-}"
VERSION="${SLIM_VERSION:-}"
PLATFORM="${SLIM_PLATFORM:-}"
ARCH="${SLIM_ARCH:-}"
OUT_DIR="${SLIM_OUT:-}"
ALL="${SLIM_ALL:-0}"

while [ $# -gt 0 ]; do
  case "$1" in
    --platform) PLATFORM="${2:-}"; shift 2 ;;
    --arch) ARCH="${2:-}"; shift 2 ;;
    --jar) JAR="${2:-}"; shift 2 ;;
    --version) VERSION="${2:-}"; shift 2 ;;
    --out) OUT_DIR="${2:-}"; shift 2 ;;
    --all) ALL=1; shift ;;
    -h|--help)
      echo "usage: $0 [--platform linux|linux-musl|windows|mac|freebsd] [--arch x86_64|aarch64] [--all]"
      exit 0 ;;
    *) echo "[slim-sqlite] ERROR: 未知参数 $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="${JAR:-$REPO_ROOT/backend/target/bluebird-task.jar}"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/backend/target/dist}"

fail() {
  echo "[slim-sqlite] ERROR: $*" >&2
  exit 1
}
info() {
  echo "[slim-sqlite] $*"
}

[ -f "$JAR" ] || fail "jar 不存在：$JAR（请先执行 spring-boot:repackage）"
command -v java >/dev/null 2>&1 || fail "未找到 java，构建机需要 JDK 21（Maven 本身已依赖）"

# ---------- detect host ----------
host_os=""
case "$(uname -s)" in
  Linux*) host_os="linux" ;;
  Darwin*) host_os="mac" ;;
  FreeBSD*) host_os="freebsd" ;;
  MINGW*|MSYS*|CYGWIN*) host_os="windows" ;;
  *) fail "无法识别的构建机系统：$(uname -s)" ;;
esac
if [ "$host_os" = "linux" ]; then
  if [ -f /etc/alpine-release ]; then
    host_os="linux-musl"
  elif command -v ldd >/dev/null 2>&1 && ldd --version 2>&1 | grep -qi musl; then
    host_os="linux-musl"
  fi
fi

raw_arch="$(uname -m)"
case "$raw_arch" in
  x86_64|amd64|AMD64) host_arch="x86_64" ;;
  aarch64|arm64|ARM64) host_arch="aarch64" ;;
  *) fail "不支持的构建机架构：$raw_arch（仅支持 x86_64/amd64、aarch64/arm64）" ;;
esac

is_ci="false"
if [ -n "${CI:-}" ] && [ "${CI}" != "false" ] && [ "${CI}" != "0" ]; then
  is_ci="true"
fi

info "host os     : $host_os"
info "host arch   : $raw_arch -> $host_arch"
info "hostname    : $(hostname 2>/dev/null || echo unknown)"
info "ci          : $is_ci"

mkdir -p "$OUT_DIR"
before_bytes=$(wc -c < "$JAR" | tr -d ' ')

sha256_of() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | cut -d' ' -f1
  else
    fail "缺少 sha256sum/shasum，无法生成校验文件"
  fi
}

build_one() {
  local platform="$1"
  local arch="$2"
  local out_name
  out_name=$(java "$SCRIPT_DIR/SlimSqlite.java" "$JAR" "$OUT_DIR" "$VERSION" "$platform" "$arch")
  out_name=$(echo "$out_name" | tail -n 1 | tr -d '\r')
  [ -n "$out_name" ] || fail "重写 jar 失败（SlimSqlite.java 未输出文件名：$platform/$arch）"
  local out_path="$OUT_DIR/$out_name"
  local sum
  sum=$(sha256_of "$out_path")
  echo "$sum  $out_name" > "$out_path.sha256"
  info "output      : $out_name ($(awk "BEGIN {printf \"%.1f\", $(wc -c < "$out_path")/1048576}") MB)"
  echo "$sum  $out_name" >> "$OUT_DIR/SHA256SUMS"
}

# ---------- all platforms/arches ----------
if [ "$ALL" = "1" ] || [ "$ALL" = "true" ]; then
  if [ "$is_ci" = "true" ]; then
    fail "CI 构建不允许 --all；请显式指定 --platform linux --arch x86_64"
  fi
  info "mode        : all platforms/arches"
  rm -f "$OUT_DIR/SHA256SUMS"
  for platform in linux linux-musl windows mac freebsd; do
    for arch in x86_64 aarch64; do
      build_one "$platform" "$arch"
    done
  done
  info "alias       : 未创建（--all 生成多个平台，请按文件名取用）"
  info "checksums   : $OUT_DIR/SHA256SUMS"
  exit 0
fi

# ---------- resolve single target ----------
target_platform="${PLATFORM:-$host_os}"
target_arch="${ARCH:-$host_arch}"
target_platform="$(echo "$target_platform" | tr '[:upper:]' '[:lower:]')"
target_arch="$(echo "$target_arch" | tr '[:upper:]' '[:lower:]')"
case "$target_arch" in
  amd64) target_arch="x86_64" ;;
  arm64) target_arch="aarch64" ;;
esac

case "$target_platform" in
  linux|linux-musl|windows|mac|freebsd) ;;
  *) fail "不支持的平台：$target_platform（可选 linux、linux-musl、windows、mac、freebsd）" ;;
esac
case "$target_arch" in
  x86_64|aarch64) ;;
  *) fail "不支持的架构：$target_arch（仅支持 x86_64/amd64、aarch64/arm64）" ;;
esac

case "$target_platform" in
  linux) native_platform="Linux" ;;
  linux-musl) native_platform="Linux-Musl" ;;
  windows) native_platform="Windows" ;;
  mac) native_platform="Mac" ;;
  freebsd) native_platform="FreeBSD" ;;
esac
keep_prefix="org/sqlite/native/$native_platform/$target_arch/"

info "target      : $target_platform / $target_arch"
info "keep        : $keep_prefix"

if [ "$is_ci" = "true" ] && { [ "$target_platform" != "linux" ] || [ "$target_arch" != "x86_64" ]; }; then
  fail "CI 构建目标必须是 linux/x86_64，当前检测到 $target_platform/$target_arch；请检查 runner 架构或显式传平台参数"
fi

out_name=$(java "$SCRIPT_DIR/SlimSqlite.java" "$JAR" "$OUT_DIR" "$VERSION" "$target_platform" "$target_arch")
out_name=$(echo "$out_name" | tail -n 1 | tr -d '\r')
[ -n "$out_name" ] || fail "重写 jar 失败（SlimSqlite.java 未输出文件名）"
out_path="$OUT_DIR/$out_name"

alias_path="$OUT_DIR/bluebird-task.jar"
ln -sfn "$out_name" "$alias_path"

sum=$(sha256_of "$out_path")
echo "$sum  $out_name" > "$out_path.sha256"

after_bytes=$(wc -c < "$out_path" | tr -d ' ')
before_mb=$(awk "BEGIN {printf \"%.1f\", $before_bytes/1048576}")
after_mb=$(awk "BEGIN {printf \"%.1f\", $after_bytes/1048576}")

info "output      : $out_path"
info "alias       : $alias_path"
info "size        : $before_mb MB -> $after_mb MB"
info "sha256      : $sum"
