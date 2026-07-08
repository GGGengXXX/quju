#!/usr/bin/env bash
# dev-bootstrap.sh — 服务器上为一个 feature 建：独立 clone + 独立 DB(含全部表) + 自动填好的 .env + 个人 push 凭证
# 用法: scripts/dev-bootstrap.sh <dev-name> <feature-slug> [module]
# 例:   scripts/dev-bootstrap.sh zhangsan activity-map activity
#
# 个人 GitHub push 凭证(每人不同)——运行前先 export(只需一次/会话):
#   export GITHUB_USER='你的 GitHub 用户名'
#   export GITHUB_TOKEN='你的 GitHub Personal Access Token'
#   export GIT_EMAIL='你的邮箱'              # 提交归属
# 不设也能跑：但 push 前你需要自行配置凭证。
#
# 设计: 每 feature 一个独立 clone(CentOS7 git 1.8 无 worktree)；从 origin/dev 切分支；
#   建独立库 quju_dev_* 并导入 contracts/schema.sql；.env 的共享密钥自动取自 /root/quju/project-secrets.env。
set -euo pipefail

DEV="${1:?用法: dev-bootstrap.sh <dev-name> <feature-slug> [module]}"
FEATURE="${2:?缺少 feature-slug}"
MODULE="${3:-misc}"

BRANCH="feat/${MODULE}-${FEATURE}"
CLONE_DIR="../dev-${DEV}-${FEATURE}"
DB_NAME="quju_dev_${DEV}_${FEATURE//-/_}"
REPO_URL="${QUJU_REPO_URL:-https://github.com/GGGengXXX/quju.git}"
HOST="github.com"

BASE_PORT=$(( 8081 + ($(echo -n "$DEV" | cksum | cut -d' ' -f1) % 50) * 10 ))
BACKEND_PORT="$BASE_PORT"; FRONTEND_PORT=$(( BASE_PORT + 100 ))

echo "==> 开发者=$DEV  feature=$FEATURE  分支=$BRANCH"
echo "==> clone=$CLONE_DIR  DB=$DB_NAME  后端端口=$BACKEND_PORT  前端端口=$FRONTEND_PORT"

# --- 1. clone + 从 dev 切 feature 分支 ---
if [ -d "$CLONE_DIR/.git" ]; then
  echo "!! $CLONE_DIR 已存在，跳过 clone"
else
  git clone --quiet "$REPO_URL" "$CLONE_DIR"
fi
cd "$CLONE_DIR"
git fetch origin --quiet
git checkout -B "$BRANCH" origin/dev
git config user.name "$DEV"
[ -n "${GIT_EMAIL:-}" ] && git config user.email "$GIT_EMAIL"

# --- 1b. 个人 push 凭证(本 clone 专属) ---
if [ -n "${GITHUB_USER:-}" ] && [ -n "${GITHUB_TOKEN:-}" ]; then
  git config --local --unset-all credential.helper 2>/dev/null || true
  git config --local --add credential.helper ''
  git config --local --add credential.helper "store --file=$(pwd)/.git/.github-cred"
  printf 'protocol=https\nhost=%s\nusername=%s\npassword=%s\n\n' "$HOST" "$GITHUB_USER" "$GITHUB_TOKEN" | git credential approve
  chmod 600 "$(pwd)/.git/.github-cred" 2>/dev/null || true
  echo "==> 已配置你个人的 GitHub push 凭证(本 clone 专属)"
else
  echo "==> 未设 GITHUB_USER/GITHUB_TOKEN：push 前请自行配置 GitHub 凭证"
fi

# --- 2. 创建隔离 DB + 导入表结构 ---
APP_PW=$(grep '^MYSQL_PASSWORD=' /root/quju/mysql.env | cut -d= -f2)
if docker exec quju-mysql mysql -uquju -p"$APP_PW" \
     -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARSET utf8mb4;" 2>/dev/null; then
  echo "==> 已创建数据库 $DB_NAME"
  if docker exec -i quju-mysql mysql -uquju -p"$APP_PW" "$DB_NAME" < contracts/schema.sql 2>/dev/null; then
    echo "==> 已导入表结构到 $DB_NAME（contracts/schema.sql）"
  else
    echo "!! 导入表结构失败：检查 contracts/schema.sql"
  fi
else
  echo "!! 建库失败：检查 quju-mysql 容器与 /root/quju/mysql.env"
fi

# --- 3. 写 .env (已 gitignore)；共享密钥自动注入 ---
[ -f /root/quju/project-secrets.env ] && . /root/quju/project-secrets.env || echo "!! 缺 /root/quju/project-secrets.env，OSS/AI/AMap/SMTP 将为空"
cat > .env <<EOF
# 自动生成 by dev-bootstrap.sh — 勿提交。共享密钥已自动注入，无需手填。
DEV_NAME=$DEV
FEATURE=$FEATURE
# 后端
SERVER_PORT=$BACKEND_PORT
DB_URL=jdbc:mysql://127.0.0.1:13306/$DB_NAME?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=quju
DB_PASSWORD=$APP_PW
REDIS_KEY_PREFIX=${DEV}:
JWT_SECRET=dev-secret-${DEV}-change-in-prod
# 前端
VITE_PORT=$FRONTEND_PORT
VITE_API_BASE=http://127.0.0.1:$BACKEND_PORT/v1
VITE_AMAP_KEY=${VITE_AMAP_KEY:-}
# 阿里云 OSS
OSS_ACCESS_KEY_ID=${OSS_ACCESS_KEY_ID:-}
OSS_ACCESS_KEY_SECRET=${OSS_ACCESS_KEY_SECRET:-}
OSS_BUCKET=se-resource-bucket
OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com
# AI (DeepSeek, OpenAI 兼容；调用务必带 User-Agent 头, 否则代理 403)
AI_BASE_URL=https://hk.n1n.ai/v1
AI_MODEL=deepseek-v3.2
AI_API_KEY=${AI_API_KEY:-}
# 邮件 (163)
SMTP_HOST=smtp.163.com
SMTP_PORT=465
SMTP_USERNAME=tluvx0806@163.com
SMTP_PASSWORD=${SMTP_PASSWORD:-}
EOF
chmod 600 .env

echo "==> 已写入 $CLONE_DIR/.env (共享密钥已注入)"
echo ""
echo "下一步:"
echo "  cd $CLONE_DIR"
echo "  (后端) cd backend && mvn spring-boot:run    # 直接可连 DB/OSS/AI/邮件"
echo "  (前端) cd frontend && npm install && npm run dev"
echo "  完成后: git push -u origin $BRANCH ，到 GitHub 建 PR 合并到 dev"
echo "  （要部署：把 dev 合并到 master → GitHub Actions 自动部署）"
