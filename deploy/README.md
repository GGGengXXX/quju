# deploy/ — 服务器部署

单台服务器（`1.92.124.5`，CentOS7 + Docker）上的部署编排。
**CI/CD = CodeArts 流水线**：合并到 `master` → 触发流水线 → **部署任务(主机组)** SSH 到本机执行 `deploy/deploy.sh`。

## 运行时拓扑
```
nginx(:80, quju-frontend) ─┬─ /        前端静态(dist)
                           └─ /v1/*    反代 → quju-backend:8080
quju-backend ── quju-mysql:3306(库 quju) ── quju-redis:6379
（均在 docker 网络 quju-net 上；对象存储走外部阿里云 OSS）
```

## 文件
- `docker-compose.yml`：backend + frontend(nginx) + redis（复用外部容器 `quju-mysql`）。
- `deploy.sh`：拉取最新 `master`(可 `DEPLOY_BRANCH` 覆盖) → `docker compose up -d --build` → 滚动重启。
- `.env.example`：部署期环境变量模板；真实值在服务器本地 `deploy/.env`（已 gitignore，勿入库）。
- `../backend/Dockerfile`、`../frontend/Dockerfile`、`../frontend/nginx.conf`：镜像构建。

## 现状（已就绪并在线）
- ✅ `quju-mysql`(mysql:8.4)、`quju-redis`、`quju-backend`、`quju-frontend` 均运行中；外部访问 http://1.92.124.5（`/v1/health` 返回 `db:UP`）。
- ✅ docker 网络 `quju-net`、数据卷已建；生产库 `quju` 已导入全部表（`contracts/schema.sql`）。
- ✅ CodeArts 流水线已配好：push/合并到 `master` 自动部署。

## CodeArts 流水线配置（已完成，备查）
- **触发**：代码提交/合并到 `master`。
- **部署任务**：CodeArts Deploy 主机组（主机 `1.92.124.5`，root，SSH 认证用 `/root/quju/codearts_deploy_key` 私钥）→ 动作「执行 Shell 命令」：`bash /srv/quju/main/deploy/deploy.sh`。
- ⚠️ 关键：必须用**能选主机组的「部署」动作**；流水线里通用的「执行shell」跑在 CodeArts 云端容器（找不到 `/srv/quju/main`，报 No such file）。

## 手动部署（应急）
```bash
cd /srv/quju/main && bash deploy/deploy.sh
```
