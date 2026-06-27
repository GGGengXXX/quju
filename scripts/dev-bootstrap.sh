#!/usr/bin/env bash
# dev-bootstrap.sh — 在服务器上为一个 feature 建独立 clone + 独立 DB + 分配端口
# 用法: scripts/dev-bootstrap.sh <dev-name> <feature-slug> [module]
# 例:   scripts/dev-bootstrap.sh zhangsan activity-map activity
#
# 设计: 每个 feature 一个独立 clone（CentOS7 自带 git 1.8 无 worktree，故用 clone）。
#   feature 从 dev 拉出；部署分支 master。流程：feature → dev(MR) → 部署时 dev→master。
# 依赖: git, docker。HTTPS 凭证已存于 root credential store（clone/push 免输）。
#   DB 用容器 quju-mysql(8.4, 宿主 13306)，账号/密码读 /root/quju/mysql.env。
set -euo pipefail

DEV="${1:?用法: dev-bootstrap.sh <dev-name> <feature-slug> [module]}"
FEATURE="${2:?缺少 feature-slug}"
MODULE="${3:-misc}"

BRANCH="feat/${MODULE}-${FEATURE}"
CLONE_DIR="../dev-${DEV}-${FEATURE}"     # 独立 clone（与主仓库 /srv/quju/main 同级）
DB_NAME="quju_dev_${DEV}_${FEATURE//-/_}"
REPO_URL="${QUJU_REPO_URL:-https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git}"

# 端口分配（保守默认；建议平台核心组按 docs/TEAM.md 固定分配，避免撞端口）
BASE_PORT=$(( 8081 + ($(echo -n "$DEV" | cksum | cut -d' ' -f1) % 50) * 10 ))
BACKEND_PORT="$BASE_PORT"
FRONTEND_PORT=$(( BASE_PORT + 100 ))

echo "==> 开发者=$DEV  feature=$FEATURE  分支=$BRANCH"
echo "==> clone=$CLONE_DIR  DB=$DB_NAME  后端端口=$BACKEND_PORT  前端端口=$FRONTEND_PORT"

# --- 1. 建独立 clone，并从 dev 切出 feature 分支 ---
if [ -d "$CLONE_DIR/.git" ]; then
  echo "!! $CLONE_DIR 已存在，跳过 clone"
else
  git clone --quiet "$REPO_URL" "$CLONE_DIR"
fi
cd "$CLONE_DIR"
git fetch origin --quiet
git checkout -B "$BRANCH" origin/dev
git config user.name "$DEV"     # 提交归属；请各自再设 git config user.email "<你的邮箱>"
echo "==> 已建分支 $BRANCH（基于 origin/dev）"

# --- 2. 创建隔离 DB schema（quju 账号已被授权 quju_dev_* 库）---
APP_PW=$(grep '^MYSQL_PASSWORD=' /root/quju/mysql.env | cut -d= -f2)
if docker exec quju-mysql mysql -uquju -p"$APP_PW" \
     -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARSET utf8mb4;" 2>/dev/null; then
  echo "==> 已创建数据库 $DB_NAME（容器 quju-mysql，宿主端口 13306）"
else
  echo "!! 建库失败：检查 quju-mysql 容器是否运行、/root/quju/mysql.env 是否可读"
fi
# <<TODO: 跑迁移基线，例如 (cd backend && mvn -Dflyway.url=jdbc:mysql://127.0.0.1:13306/$DB_NAME flyway:migrate)>>

# --- 3. 写该 clone 的 .env (已 gitignore) ---
cat > .env <<EOF
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
# AI (DeepSeek, OpenAI 兼容；调用时务必带 User-Agent 头，否则代理返回 403)
AI_BASE_URL=https://hk.n1n.ai/v1
AI_MODEL=deepseek-v3.2
AI_API_KEY=<<TODO: 见 deepseek-api-key.txt>>
# 高德地图 / 邮件 (值见对应 txt; 不入库)
VITE_AMAP_KEY=<<TODO: 见 高德地图-api-key.txt>>
SMTP_HOST=smtp.163.com
SMTP_PORT=465
SMTP_USERNAME=tluvx0806@163.com
SMTP_PASSWORD=<<TODO: 见 邮箱验证配置信息.txt>>
EOF

echo "==> 已写入 $CLONE_DIR/.env"
echo ""
echo "下一步:"
echo "  cd $CLONE_DIR"
echo "  (后端) cd backend && mvn spring-boot:run"
echo "  (前端) cd frontend && npm install && npm run dev"
echo "  完成后: git push -u origin $BRANCH ，到 CodeArts 建 MR 合并到 dev"
echo "  （要部署时：把 dev 合并到 master → 触发 CodeArts 流水线自动部署）"
