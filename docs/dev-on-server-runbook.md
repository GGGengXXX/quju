# 服务器开发手册（每人独立 clone + 隔离 DB / 端口）

> 所有人**直接在服务器上**开发，不在本地。每个 feature 一个独立 clone（一分支），互不干扰。
> （CentOS7 自带 git 1.8 无 `worktree`，故用独立 clone；隔离性更好。）
> 共用一台 MySQL/Redis，但**每人一个独立 DB schema + 一段端口**，避免迁移/数据/端口冲突。

## 0. 一次性：服务器准备（平台核心组做）

服务器 `1.92.124.5`（CentOS 7，root 登录，密码见 `HuaweiCloud.txt`，**勿入库**）。Docker 26.x 已装。

- 已就绪：Docker、MySQL（容器 `quju-mysql`）、Git 1.8。开发需：JDK 17、Maven、Node 18+、各人的 AI CLI（Claude Code / Cursor server / …）。Redis 由部署编排提供（见 `deploy/`）。
- 在固定位置克隆"主/部署仓库"一次（部署用，分支 master）：
  ```bash
  sudo mkdir -p /srv/quju && sudo chown $USER /srv/quju
  cd /srv/quju
  # 默认从 GitHub clone；偏好 SSH 者先 export QUJU_REPO_URL=git@github.com:GGGengXXX/quju.git
  git clone "${QUJU_REPO_URL:-https://github.com/GGGengXXX/quju.git}" main   # 主工作目录（GitHub）
  ```
- **MySQL 已就绪**：docker 容器 `quju-mysql`(mysql:8.4)，宿主端口 `13306`，生产库 `quju`，应用账号 `quju`；凭证在 `/root/quju/mysql.env`(root-only，**勿入库**)。`dev-bootstrap.sh` 用 `quju` 账号为每人建独立 `quju_dev_*` 库（已授权）。

## 1. 每人/每 feature：建一个独立 clone

在 `/srv/quju/main` 下运行 bootstrap 脚本（见 `scripts/dev-bootstrap.sh`）：

```bash
cd /srv/quju/main
# 一次性设你的个人身份 + GitHub push 凭证（每人不同；当前 SSH 会话内有效）：
export GIT_EMAIL='你的邮箱'
export GITHUB_USER='你的 GitHub 用户名'
export GITHUB_TOKEN='你的 GitHub Personal Access Token'
scripts/dev-bootstrap.sh <你的名字拼音> <feature-slug> [module]
# 例：scripts/dev-bootstrap.sh zhangsan activity-map activity
```

脚本会自动：
1. 从 GitHub 克隆到 `/srv/quju/dev-<name>-<feature>`，并基于 `origin/dev` 切出 `feat/<module>-<feature>` 分支
2. 建独立库 `quju_dev_<name>_<feature>` 并**导入全部表结构**（contracts/schema.sql）
3. 生成 `.env`：端口/DB + **OSS/AI/高德/邮件 等共享密钥自动注入（无需手填）**
4. 配好你个人的 push 凭证（本 clone 专属；不设则需要你后续自行配置）

然后进入你的 clone 开发（`.env` 已就绪，可直接连 DB/OSS/AI/邮件）：
```bash
cd /srv/quju/dev-<name>-<feature>
# 后端
cd backend && mvn spring-boot:run
# 前端（另开终端）
cd frontend && npm install && npm run dev
```

## 2. 端口 / DB 分配表（避免撞端口）

每人一段 10 个端口。后端 = 段首，前端 = 段首+1，调试 = 段首+2…

| 开发者 | 后端端口 | 前端端口 | DB schema 前缀 | Redis 前缀 |
|---|---|---|---|---|
| `<<TODO 1>>` | 8081 | 5181 | quju_dev_1_ | dev1: |
| `<<TODO 2>>` | 8091 | 5191 | quju_dev_2_ | dev2: |
| `<<TODO 3>>` | 8101 | 5201 | quju_dev_3_ | dev3: |
| … | … | … | … | … |

> 共 10 人，按此规律排到第 10 段。`dev-bootstrap.sh` 读这张表（或读 `.env` 模板）来分配。
> `<<TODO: 平台核心组确认端口区间不与服务器其它服务冲突>>`

## 3. 日常

```bash
# 同步 dev（在你的 clone 里）
git fetch origin && git rebase origin/dev       # feature 跟随 dev；或 merge，团队统一

# 契约变了 → 重新生成（在 contracts/ 或用 make）
make gen-backend && make gen-frontend

# 提交 & 推送 & 开 PR
git add -p && git commit -m "<module>: ..."     # 信息见 AGENTS §7
git push -u origin feat/<module>-<feature>
# 然后在 GitHub 新建 Pull Request，目标 dev，描述用 docs/merge-request-template.md 或 .github/pull_request_template.md（部署时 dev→master）
```

## 4. 收尾：删除 clone

MR 合并后清理，释放磁盘与端口：
```bash
rm -rf /srv/quju/dev-<name>-<feature>           # 直接删该 clone 目录
# 可选：DROP DATABASE quju_dev_<name>_<feature>;
```

## 5. 注意

- **不要**在 `/srv/quju/main` 主工作目录里直接改代码 —— 它是部署用的（master），由流水线维护。
- 每人只在自己的 clone + 自己负责的目录里改（见 `work-assignment.md`）。
- 密钥放各自 `.env`（已 gitignore），不要提交。
