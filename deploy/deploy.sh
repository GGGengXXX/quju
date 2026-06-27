#!/usr/bin/env bash
# 服务器部署脚本：拉取最新 main → 构建镜像 → 滚动重启。
# CodeArts 的部署任务 / Webhook 在 main 更新后调用本脚本即可。
# 也可手动执行：bash deploy/deploy.sh
set -euo pipefail

REPO_DIR="${REPO_DIR:-/srv/quju/main}"
COMPOSE="docker compose -f deploy/docker-compose.yml --env-file deploy/.env"

cd "$REPO_DIR"
echo "[deploy] 仓库目录: $REPO_DIR"

# 1) 取最新 main
git fetch --all --prune
git checkout main
git reset --hard origin/main
echo "[deploy] 已更新到 $(git rev-parse --short HEAD)"

# 2) 确保外部网络 / 部署 env 存在
docker network inspect quju-net >/dev/null 2>&1 || docker network create quju-net >/dev/null
if [ ! -f deploy/.env ]; then
  echo "[deploy] 缺少 deploy/.env（服务器本地，含 DB/OSS/JWT）。参考 deploy/.env.example 创建。" >&2
  exit 1
fi

# 3) 构建并滚动重启（仅重建变化的服务）
$COMPOSE up -d --build
docker image prune -f >/dev/null 2>&1 || true

echo "[deploy] 完成。容器状态："
docker ps --filter name=quju- --format "  {{.Names}} | {{.Status}} | {{.Ports}}"
