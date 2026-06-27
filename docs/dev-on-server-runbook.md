# 服务器开发手册（worktree + 每人隔离 DB / 端口）

> 所有人**直接在服务器上**开发，不在本地。每个 feature 一分支一 worktree，互不干扰。
> 共用一台 MySQL/Redis，但**每人一个独立 DB schema + 一段端口**，避免迁移/数据/端口冲突。

## 0. 一次性：服务器准备（平台核心组做）

服务器 `1.92.124.5`（CentOS 7，root 登录，密码见 `HuaweiCloud.txt`，**勿入库**）。Docker 26.x 已装。

- 已就绪：Docker、MySQL（容器 `quju-mysql`）。各 worktree 需要：JDK 17、Maven、Node 18+、Git（缺则 yum/手装）、各人的 AI CLI（Claude Code / Cursor server / …）。Redis 由部署编排提供（见 `deploy/`）。
- 在固定位置克隆"主仓库"一次（**裸/主**仓库，供大家 add worktree）：
  ```bash
  sudo mkdir -p /srv/quju && sudo chown $USER /srv/quju
  cd /srv/quju
  git clone https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git main   # 主工作目录（CodeArts）
  ```
- **MySQL 已就绪**：docker 容器 `quju-mysql`(mysql:9.0.1)，宿主端口 `13306`，生产库 `quju`，应用账号 `quju`；凭证在 `/root/quju/mysql.env`(root-only，**勿入库**)。`dev-bootstrap.sh` 用 `quju` 账号为每人建独立 `quju_dev_*` 库（已授权）。

## 1. 每人/每 feature：开一个 worktree

在 `/srv/quju/main` 下运行 bootstrap 脚本（见 `scripts/dev-bootstrap.sh`）：

```bash
cd /srv/quju/main
scripts/dev-bootstrap.sh <你的名字拼音> <feature-slug>
# 例：scripts/dev-bootstrap.sh zhangsan activity-map
```

脚本会自动：
1. `git fetch && git worktree add ../wt-<name>-<feature> -b feat/<module>-<feature> origin/main`
2. 建独立库：`CREATE DATABASE quju_dev_<name>_<feature>`（从 schema 基线迁移）
3. 生成该 worktree 的 `.env`：分配后端/前端端口（见 §2 端口表）、DB 名、Redis key 前缀
4. 打印"下一步"提示

然后进入你的 worktree 开发：
```bash
cd /srv/quju/wt-<name>-<feature>
# 后端
cd backend && mvn spring-boot:run
# 前端（另开一个终端/复用）
cd frontend && npm ci && npm run dev
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
# 同步主干（在你的 worktree 里）
git fetch origin && git rebase origin/main      # 或 merge，团队统一

# 契约变了 → 重新生成（在 contracts/ 或用 make）
make gen-backend && make gen-frontend

# 提交 & 推送 & 开 PR
git add -p && git commit -m "<module>: ..."     # 信息见 AGENTS §7
git push -u origin feat/<module>-<feature>
# 然后在 CodeArts 网页新建合并请求(MR)，目标 main，描述用 docs/merge-request-template.md
```

## 4. 收尾：删除 worktree

PR 合并后清理，释放磁盘与端口：
```bash
cd /srv/quju/main
git worktree remove ../wt-<name>-<feature>
git branch -d feat/<module>-<feature>           # 远端分支由 PR 合并时删
# 可选：DROP DATABASE quju_dev_<name>_<feature>;
```

## 5. 注意

- **不要**在 `main` 主工作目录里直接改代码 —— 它只用来 add worktree / 跑 CI 基线。
- 每人只在自己的 worktree + 自己负责的目录里改（见 `work-assignment.md`）。
- 密钥放各自 `.env`（已 gitignore），不要提交。
