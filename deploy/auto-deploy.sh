#!/usr/bin/env bash
# 自动部署看护：检测部署分支(默认 master)是否有新提交，有则触发 deploy.sh。
# 由 systemd timer 每 ~30s 调用一次（见 deploy/systemd/）。日志写 /root/quju/auto-deploy.log。
# 等价于"合并到 master → 自动部署"：合并后 ≤30s 内自动拉取并重新部署。
set -euo pipefail

REPO_DIR="${REPO_DIR:-/srv/quju/main}"
BRANCH="${DEPLOY_BRANCH:-master}"
LOG="${AUTODEPLOY_LOG:-/root/quju/auto-deploy.log}"

cd "$REPO_DIR"
# 显式 refspec：兼容 CentOS7 的 git 1.8（带 refspec 的 fetch 不会更新 origin/<b> 跟踪引用）
git fetch origin "$BRANCH:refs/remotes/origin/$BRANCH" --quiet || exit 0
LOCAL=$(git rev-parse "$BRANCH" 2>/dev/null || echo none)
REMOTE=$(git rev-parse "origin/$BRANCH")

if [ "$LOCAL" != "$REMOTE" ]; then
  echo "[$(date -Iseconds)] $BRANCH 有更新 ${LOCAL:0:7} -> ${REMOTE:0:7}，开始部署" >> "$LOG"
  if DEPLOY_BRANCH="$BRANCH" bash deploy/deploy.sh >> "$LOG" 2>&1; then
    echo "[$(date -Iseconds)] 部署完成 -> $(git rev-parse --short HEAD)" >> "$LOG"
  else
    echo "[$(date -Iseconds)] !! 部署失败，见上方日志" >> "$LOG"
  fi
fi
