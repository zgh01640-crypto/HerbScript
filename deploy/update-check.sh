#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/docker-compose.prod.yml}"

cd "$ROOT_DIR"

echo "== HerbScript 服务器更新检查 =="
echo "项目目录: $ROOT_DIR"
echo

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "❌ 未找到生产编排文件: $COMPOSE_FILE"
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "⚠️  未找到环境变量文件: $ENV_FILE"
  echo "    如需生产部署，请先准备 .env.prod"
else
  echo "✅ 已找到环境变量文件: $ENV_FILE"
fi

echo
echo "== Git 状态 =="
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "❌ 当前目录不是 Git 仓库"
  exit 1
fi

BRANCH_NAME="$(git rev-parse --abbrev-ref HEAD)"
echo "当前分支: $BRANCH_NAME"

if [[ -n "$(git status --short)" ]]; then
  echo "⚠️  工作区存在本地改动，请先确认是否可以直接更新："
  git status --short
  echo
  echo "建议："
  echo "1. 先执行 git diff 查看改动"
  echo "2. 确认这些改动是否已在本地仓库/远程仓库中"
  echo "3. 再决定是否 git restore 或 git stash"
else
  echo "✅ 工作区干净，可直接执行："
  echo "   git pull origin $BRANCH_NAME"
fi

echo
echo "== 生产编排配置检查 =="
if grep -q 'SPRING_CORS_ALLOWED_ORIGINS' "$COMPOSE_FILE"; then
  echo "✅ docker-compose.prod.yml 已透传 SPRING_CORS_ALLOWED_ORIGINS"
else
  echo "⚠️  docker-compose.prod.yml 未透传 SPRING_CORS_ALLOWED_ORIGINS"
fi

if [[ -f "$ENV_FILE" ]]; then
  PUBLIC_HTTP_PORT_VALUE="$(grep '^PUBLIC_HTTP_PORT=' "$ENV_FILE" | cut -d'=' -f2- || true)"
  CORS_VALUE="$(grep '^SPRING_CORS_ALLOWED_ORIGINS=' "$ENV_FILE" | cut -d'=' -f2- || true)"
  DOMAIN_VALUE="$(grep '^APP_DOMAIN=' "$ENV_FILE" | cut -d'=' -f2- || true)"

  echo "PUBLIC_HTTP_PORT: ${PUBLIC_HTTP_PORT_VALUE:-未配置}"
  echo "APP_DOMAIN: ${DOMAIN_VALUE:-未配置}"
  echo "SPRING_CORS_ALLOWED_ORIGINS: ${CORS_VALUE:-未配置}"
fi

echo
echo "== 推荐更新命令 =="
echo "git pull origin $BRANCH_NAME"
echo "docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build"

