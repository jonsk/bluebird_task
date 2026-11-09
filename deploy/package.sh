#!/usr/bin/env bash
# 本地构建单制品 jar（内嵌前端，ADR-009）。
#
# 顺序不能省：前端 build 产出 frontend/dist，后端 package 再把整个 dist 拷进 jar。
# Tiptap 相关依赖全部由 Vite 打进 bundle，不再需要额外的运行期静态资源目录。
#
# 用法：
#   deploy/package.sh                 # 跳过测试，产物 deploy/out/bluebird-task.jar
#   deploy/package.sh --with-tests    # 先跑后端测试再打包
#   deploy/package.sh --out /tmp/x.jar
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$ROOT/frontend"
BACKEND="$ROOT/backend"
OUT="$ROOT/deploy/out/bluebird-task.jar"
WITH_TESTS=0

usage() {
  sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-tests) WITH_TESTS=1; shift ;;
    --out) OUT="${2:?--out 需要路径}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 2 ;;
  esac
done

die() { echo "!! $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }

command -v node >/dev/null 2>&1 || die "缺少 node"
command -v pnpm >/dev/null 2>&1 || die "缺少 pnpm（corepack enable）"
command -v mvn >/dev/null 2>&1 || die "缺少 mvn"
command -v java >/dev/null 2>&1 || die "缺少 java"

node_major="$(node -p 'process.versions.node.split(".")[0]')"
[[ "$node_major" -ge 20 ]] || die "node 需要 >= 20，当前 $(node -v)"

java_version="$(java -version 2>&1 | head -1)"
java_major="$(sed -n 's/.*version "\([0-9]*\).*/\1/p' <<<"$java_version" | head -1)"
[[ -n "$java_major" && "$java_major" -ge 21 ]] || die "java 需要 >= 21，当前 $java_version"

step "前端依赖"
pnpm -C "$FRONTEND" install --frozen-lockfile

step "前端构建"
pnpm -C "$FRONTEND" build

if [[ "$WITH_TESTS" -eq 1 ]]; then
  step "后端测试 + 打包"
  mvn -B -f "$BACKEND/pom.xml" package
else
  step "后端打包（跳过测试，加 --with-tests 可先跑测试）"
  mvn -B -f "$BACKEND/pom.xml" -DskipTests package
fi

JAR="$BACKEND/target/bluebird-task.jar"
[[ -f "$JAR" ]] || die "打包未产出 $JAR"

step "校验 jar 内嵌产物"
if command -v unzip >/dev/null 2>&1; then
  listing="$(unzip -l "$JAR")"
  for need in "BOOT-INF/classes/static/index.html"; do
    grep -q "$need" <<<"$listing" || die "jar 缺少 $need（前端产物未内嵌？）"
  done
else
  echo "!! 未找到 unzip，跳过 jar 内容校验"
fi

mkdir -p "$(dirname "$OUT")"
cp -f "$JAR" "$OUT"

if command -v sha256sum >/dev/null 2>&1; then
  sum="$(sha256sum "$OUT" | cut -d' ' -f1)"
else
  sum="$(shasum -a 256 "$OUT" | cut -d' ' -f1)"
fi

printf '\n==> 完成：%s（%s）\n' "$OUT" "$(du -h "$OUT" | cut -f1)"
printf '    sha256: %s\n' "$sum"
printf '    下一步：服务器上直接跑 deploy/deploy.sh（默认会自己拉代码 + 构建 + 部署）\n'
