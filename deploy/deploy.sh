#!/usr/bin/env bash
# 服务器部署脚本：拉取最新 master → 构建镜像 → 滚动重启。
# CodeArts 流水线/Webhook 在 master 更新(dev 合并入 master)后调用本脚本即可。
# 部署分支可用 DEPLOY_BRANCH 覆盖（默认 master）。也可手动执行：bash deploy/deploy.sh
set -euo pipefail

# 启用 BuildKit 以支持 Dockerfile 的 cache mount，加速重复部署。
export DOCKER_BUILDKIT=1
export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"

REPO_DIR="${REPO_DIR:-/srv/quju/main}"
COMPOSE="docker compose -f deploy/docker-compose.yml --env-file deploy/.env"

cd "$REPO_DIR"
echo "[deploy] 仓库目录: $REPO_DIR"

# 1) 取最新部署分支（默认 master）
BRANCH="${DEPLOY_BRANCH:-master}"
git fetch --all --prune
git checkout "$BRANCH"
git reset --hard "origin/$BRANCH"
echo "[deploy] 已更新 $BRANCH 到 $(git rev-parse --short HEAD)"

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
