#!/usr/bin/env bash
# dev-bootstrap.sh — 在服务器上为一个 feature 开 worktree + 独立 DB schema + 分配端口
# 用法: scripts/dev-bootstrap.sh <dev-name> <feature-slug> [module]
# 例:   scripts/dev-bootstrap.sh zhangsan activity-map activity
#
# 约定: 在主仓库目录 (/srv/quju/main) 内运行。worktree 建在同级目录。
# 依赖: git, docker。DB 用服务器上的容器 quju-mysql(mysql:8.4, 宿主端口 13306)，
#   应用账号/密码读自 /root/quju/mysql.env(root-only, 不入库)。
set -euo pipefail

DEV="${1:?用法: dev-bootstrap.sh <dev-name> <feature-slug> [module]}"
FEATURE="${2:?缺少 feature-slug}"
MODULE="${3:-misc}"

BRANCH="feat/${MODULE}-${FEATURE}"
WT_DIR="../wt-${DEV}-${FEATURE}"
DB_NAME="quju_dev_${DEV}_${FEATURE//-/_}"

# --- 端口分配: 从 docs/TEAM.md 的分配，或按 dev 简单哈希。这里给个保守默认。 ---
# <<TODO: 平台核心组改成读 TEAM.md 的固定分配表，避免撞端口>>
BASE_PORT=$(( 8081 + ($(echo -n "$DEV" | cksum | cut -d' ' -f1) % 50) * 10 ))
BACKEND_PORT="$BASE_PORT"
FRONTEND_PORT=$(( BASE_PORT + 100 ))

echo "==> 开发者=$DEV  feature=$FEATURE  分支=$BRANCH"
echo "==> worktree=$WT_DIR  DB=$DB_NAME  后端端口=$BACKEND_PORT  前端端口=$FRONTEND_PORT"

# --- 1. 创建 worktree ---
git fetch origin
if git show-ref --quiet "refs/heads/$BRANCH"; then
  git worktree add "$WT_DIR" "$BRANCH"
else
  git worktree add "$WT_DIR" -b "$BRANCH" origin/main
fi

# --- 2. 创建隔离 DB schema（docker 容器 quju-mysql；quju 账号已被授权 quju_dev_* 库）---
APP_PW=$(grep '^MYSQL_PASSWORD=' /root/quju/mysql.env | cut -d= -f2)
if docker exec quju-mysql mysql -uquju -p"$APP_PW" \
     -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARSET utf8mb4;" 2>/dev/null; then
  echo "==> 已创建数据库 $DB_NAME（容器 quju-mysql，宿主端口 13306）"
else
  echo "!! 建库失败：检查 quju-mysql 容器是否运行、/root/quju/mysql.env 是否可读"
fi
# <<TODO: 跑迁移基线，例如 (cd "$WT_DIR/backend" && mvn -Dflyway.url=jdbc:mysql://127.0.0.1:13306/$DB_NAME flyway:migrate)>>

# --- 3. 写该 worktree 的 .env (已 gitignore) ---
cat > "$WT_DIR/.env" <<EOF
# 自动生成 by dev-bootstrap.sh — 勿提交
DEV_NAME=$DEV
FEATURE=$FEATURE
# 后端
SERVER_PORT=$BACKEND_PORT
DB_URL=jdbc:mysql://127.0.0.1:13306/$DB_NAME?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=quju
DB_PASSWORD=$APP_PW
REDIS_KEY_PREFIX=${DEV}:
# 前端
VITE_PORT=$FRONTEND_PORT
VITE_API_BASE=http://127.0.0.1:$BACKEND_PORT/v1
# 阿里云 OSS (值见 AccessKey.csv; 不入库)
OSS_ACCESS_KEY_ID=<<TODO: 见 AccessKey.csv>>
OSS_ACCESS_KEY_SECRET=<<TODO: 见 AccessKey.csv>>
OSS_BUCKET=se-resource-bucket
OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com
# 其它第三方 (按需填; 不入库)
VITE_AMAP_KEY=<<TODO: 高德地图 key>>
AI_API_KEY=<<TODO: LLM/视觉 服务 key>>
SMTP_URL=<<TODO: 邮件激活>>
EOF

echo "==> 已写入 $WT_DIR/.env"
echo ""
echo "下一步:"
echo "  cd $WT_DIR"
echo "  (后端) cd backend && mvn spring-boot:run"
echo "  (前端) cd frontend && npm ci && npm run dev"
echo "  完成后: git push -u origin $BRANCH ，再到 CodeArts 网页建合并请求(MR)到 main"
