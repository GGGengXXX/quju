# deploy/ — 服务器自托管部署

单台服务器（`1.92.124.5`，CentOS7 + Docker）上的部署编排。CI/CD 模型：
**CodeArts 上 `main` 更新 → 触发部署任务 → 在服务器执行 `deploy/deploy.sh` → docker compose 滚动重启。**

## 运行时拓扑
```
nginx(:80, quju-frontend) ─┬─ /        前端静态(dist)
                           └─ /v1/*    反代 → quju-backend:8080
quju-backend ── quju-mysql:3306(库 quju) ── quju-redis:6379
（均在 docker 网络 quju-net 上；OSS 走外部阿里云）
```

## 现状（已就绪）
- ✅ `quju-mysql`(mysql:8.4) 已运行：宿主端口 13306，库 `quju`，账号 `quju`，凭证 `/root/quju/mysql.env`。
- ✅ docker 网络 `quju-net`、数据卷 `quju-mysql-data` 已创建。
- ⬜ `quju-redis`：首次 `docker compose ... up` 时拉取 `redis:7-alpine`（hub 不可达则配镜像加速）。
- ⬜ `backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf`：随应用脚手架补全（模板已给）。

## 前置（一次性）
1. 服务器克隆主仓库到 `/srv/quju/main`（见 `docs/dev-on-server-runbook.md`）。
2. `cp deploy/.env.example deploy/.env` 并填值（DB_PASSWORD 取自 `/root/quju/mysql.env`，OSS 取自 `AccessKey.csv`）。`deploy/.env` 不入库。

## 手动部署
```bash
cd /srv/quju/main && bash deploy/deploy.sh
```

## 接入 CodeArts（二选一，见 ../docs/codearts-and-cicd.md 第三节）
- **方案 A**：CodeArts Pipeline（触发=合并到 main）→ Deploy 任务 SSH 到本机执行 `deploy/deploy.sh`。
- **方案 B**：CodeArts 仓库 Webhook → 本机监听器收到 main 事件 → 执行 `deploy/deploy.sh`。

> CodeArts SSH 到本机所需凭证：本机已生成专用部署密钥 `/root/quju/codearts_deploy_key`（公钥已加入 authorized_keys）。在 CodeArts 主机配置里用该私钥或服务器密码均可。详见 codearts 文档。
