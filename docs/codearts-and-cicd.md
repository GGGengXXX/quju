# 华为云 CodeArts：仓库 / 代码推送 / CI-CD（服务器自托管部署）

> 代码托管在**华为云 CodeArts Repo（原 CodeHub）**，不在 GitHub。CI/CD 在**服务器自身**完成：
> 服务器上开发 → push → 合并请求(MR)合并 → 自动在服务器上构建并部署。
> 参考：[CodeArts Repo 入门](https://support.huaweicloud.com/intl/zh-cn/qs-codehub/codehub_qs_1000.html) ·
> [CodeArts Pipeline 使用流程](https://support.huaweicloud.com/usermanual-pipeline/pipeline_01_0013.html)

| 资源 | 值 |
|---|---|
| 仓库地址 | `https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git` |
| 区域 | cn-north-4（北京四） |
| 部署服务器 | `1.92.124.5`（密码见 `HuaweiCloud.txt`，**不入库**；详见 `secrets-and-config.md`） |

---

## 〇、分支模型（master / dev / feature）

- **`master`**：主分支 / 生产分支，**受保护**。**合并到 master = 触发服务器自动部署**。
- **`dev`**：集成分支，所有 feature 先合并到这里联调。
- **feature 分支**：从 `dev` 拉出（如 `feat/activity-map`），开发完 MR 合并回 `dev`。
- **部署**：联调 OK 后把 `dev` 合并到 `master`（MR）→ CodeArts 触发流水线 → 服务器 `deploy/deploy.sh` 拉 master 重新部署。

`feature ──MR──▶ dev ──MR(部署)──▶ master ──触发──▶ 服务器自动部署`

> `dev-bootstrap.sh` 默认从 `origin/dev` 开 feature；`deploy/deploy.sh` 默认部署 `master`（可 `DEPLOY_BRANCH` 覆盖）。

---

## 一、仓库创建（已存在，下面是从零的方法 + 首推）

仓库已创建好（URL 见上）。若需新建：CodeArts 控制台 → 代码托管 → 新建仓库 → 选「普通仓库」→ 填名称 `quju` → 不勾选自动生成 README（我们要 push 现有工作区）。

### 把本工作区首次推送上去

```bash
cd quju
git init -b main
git add .
git commit -m "chore: 初始化 QuJu AI 开发工作区（治理层 + 契约骨架）"
git remote add origin https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git
git push -u origin main
```

**代码推送鉴权（默认 HTTPS；部署始终 SSH）**：
- **HTTPS（默认，用于代码上传/push）**：用 CodeArts **代码托管 HTTPS 密码**（控制台 → 个人设置 → 代码托管/HTTPS 密码：设置并复制；用户名以该页显示为准）。`git push` 时填入，凭证管理器（macOS Keychain / `git config --global credential.helper store`）可记住，后续免输。
  - ⚠️ **华为云 IAM AK/SK（Access Key/Secret，如 `credentials.csv`）不是 git HTTPS 密码**，拿它 push 会被拒（`HTTP Basic: Access denied`）。务必用上面的「HTTPS 密码」。
- **SSH（可选，按队员喜好）**：把个人 `~/.ssh/id_ed25519.pub` 加到 CodeArts 个人 SSH 公钥；remote 用 `git@codehub.devcloud.cn-north-4.huaweicloud.com:5c09170aa96c46008547da02db15afa0/quju.git`。
- **协议可配置**：由 remote URL 决定；脚本用 `QUJU_REPO_URL` 环境变量切换（默认 HTTPS，偏好 SSH 者改为 `git@…` 形式）。
- **部署链路始终走 SSH 密钥**（CodeArts→服务器 部署、服务器→CodeArts 拉代码），与个人 push 用什么协议无关，见第五节。

---

## 二、代码推送 + 合并请求(MR) 流程

CodeArts 用**合并请求（Merge Request, MR）**而非 GitHub PR。日常（在你的 clone 内）：

```bash
git push -u origin feat/<module>-<slug>
```
然后在 CodeArts 网页 → 代码托管 → 合并请求 → 新建 MR：源分支 = 你的 feature 分支，**目标 = `dev`**，描述粘贴 `docs/merge-request-template.md`，指定评审人。（部署时再 `dev`→`master`。）

### 用分支保护 + 评审来落地"契约即法律"（替代 GitHub CODEOWNERS）

CodeArts 没有 GitHub 的路径级 CODEOWNERS，自动按目录指派 owner 的能力较弱。用以下组合达到同等约束：

1. **保护 `main` 分支**：仓库 → 设置 → 分支管理/保护分支 → `main` 设为受保护：禁止直接 push、必须经 MR、要求 ≥1 通过评审、要求 CI 门禁通过。
2. **平台核心组设为必选评审人**：仓库 → 设置 → 合并请求/评审规则 → 添加平台核心 2 人为必需评审者（或建评审组）。
3. **约定**：改 `contracts/` 必须**单独 MR** + 先有 ADR；平台核心组在该 MR 上把关。MR 模板里有自检项强制声明"是否动了 contracts/"。
4. （可选，若你们版本支持）「评审模板」「门禁(Gate)」：把测试/契约校验设为合并门禁。

---

## 三、CI/CD：服务器自托管（需要你选一种方案）

目标：合并到 `master` 后，**自动在 `1.92.124.5` 上**构建后端 jar、构建前端、重启服务。两种主流做法：

### 方案 A：CodeArts Pipeline + 自定义执行机（华为云原生，推荐用于课程交付）
- 在服务器上安装 **CodeArts 自定义执行机（agent）**，注册到你们的 CodeArts 项目。
- 建一条 **Pipeline**：触发器 = 「push 到 master / 合并请求合并到 master」；阶段 = 代码检查 → 编译构建(Maven + npm) → 部署(在自定义执行机即本服务器上跑 `deploy/deploy.sh`)。
- 好处：可视化、有构建/检查/部署记录、符合"用 CodeArts 全流程"的课程预期。
- 代价：需配置执行机 + Pipeline；自动化创建需 API（见第四节）。
- 参考：[Pipeline 使用流程](https://support.huaweicloud.com/usermanual-pipeline/pipeline_01_0013.html)、[服务扩展点](https://support.huaweicloud.com/usermanual-pipeline/pipeline_01_0011.html)。

### 方案 B：CodeArts Repo Webhook → 服务器部署监听器（最简、纯服务器）
- 服务器上跑一个轻量 webhook 监听（小脚本/服务），CodeArts 仓库 → 设置 → Webhook 指向它。
- `main` 有合并/push 事件 → 监听器校验签名 → 执行 `deploy/deploy.sh`（拉取 main → `mvn package` → `npm build` → 重启）。
- 好处：不依赖华为执行机/Pipeline，完全在服务器自洽，易懂易调。
- 代价：评审/构建记录不在 CodeArts 里可视化（CI 体感弱一些）。

> 两种方案都需要服务器上的 `deploy/deploy.sh` 与运行时编排（建议 `deploy/docker-compose.yml`：MySQL8 + Redis + backend + frontend(nginx)）。**待你选定方案后我立即生成这套部署脚本。**

### 运行时拓扑（建议，服务器单机）
```
nginx(443/80) ─┬─ /        → 前端静态(dist)
               └─ /v1/*    → 后端 Spring Boot(:8080)
后端 ── MySQL8(:3306, 生产库 quju) ── Redis(:6379)
对象存储 → 阿里云 OSS(se-resource-bucket)（外部，非本机）
```
> 注意：开发期每人用独立 schema（`quju_dev_*`），**生产/部署用单独的 `quju` 库**，与开发库隔离。

---

## 四、若要我帮忙"自动化"，需要你提供的 API / 凭证

按你想自动化的程度，分级提供（都不会写进仓库，仅用于一次性配置）：

- **只手动按文档做** → 不需要给我任何额外凭证（仓库 URL + 服务器已够，文档已写全）。
- **要我直接登服务器搭好 CI/CD（方案 B 最快）** → 授权我用 `HuaweiCloud.txt` 里的 `1.92.124.5` + 密码 SSH 登录配置（这是对你服务器的实际操作，我会先和你确认每一步）。建议同时给我：服务器 OS（`cat /etc/os-release`）、是否允许装 Docker。
- **要我用 CodeArts OpenAPI 自动建 Pipeline/Webhook/分支保护（方案 A）** → 需要：华为云 **IAM AK/SK**（有 CodeArts 权限）、**region**=cn-north-4、**CodeArts 项目 ID / 仓库 ID**（URL 里的 `5c09170aa96c46008547da02db15afa0` 是仓库/项目标识，请确认是项目 ID 还是仓库 ID）、以及推送用的 **SSH 部署密钥或 HTTPS 凭证**。
  - 参考：[CodeArts OpenAPI](https://support.huaweicloud.com/api-codeartspipeline/pipeline_07_0001.html)（Pipeline）、CodeArts Repo API。

> 你的诉求"代码上传 CodeArts 后通过 action 在服务器部署"对应 **方案 A**（CodeArts Pipeline 的部署任务 SSH 到服务器执行 `deploy/deploy.sh`）。下面第五节是已就绪部分与你要做的事。

---

## 五、当前已就绪 & 你需要做/提供的（具体）

### ✅ 服务器侧我已搭好
- MySQL：容器 `quju-mysql`(mysql:8.4)，宿主端口 `13306`，库 `quju`，账号 `quju`，凭证 `/root/quju/mysql.env`。
- docker 网络 `quju-net`、数据卷 `quju-mysql-data`。
- `deploy/`：`docker-compose.yml` + `deploy.sh` + `.env.example`；`backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf` 模板。
- 两把 SSH 部署密钥（在 `/root/quju/`）：
  - `codearts_deploy_key`：**CodeArts→服务器**部署用，公钥已入服务器 `authorized_keys`。
  - `codearts_repo_key`：**服务器→CodeArts**拉代码用（`deploy.sh` 的 `git pull`），已配 `~/.ssh/config`。

### ⬜ 需要你做（按顺序）
1. **把仓库只读拉取公钥加到 CodeArts**（让服务器能 pull 部署）：
   公钥 = `/root/quju/codearts_repo_key.pub`（内容我已在会话里给出）。
   优先加为**仓库级部署密钥(只读)**：仓库 → 设置 → 部署密钥；若无该功能则加到一个服务账号的 SSH 公钥。
2. **首次把工作区推到 CodeArts**（用真实学号账号提交，便于课程统计）：见第一节命令。之后服务器 `git clone ... /srv/quju/main`。
3. **建 CodeArts 流水线（方案 A）**：触发=push/合并到 `master` → 构建(可选) → **部署任务**：目标主机 `1.92.124.5`（SSH，认证用 `codearts_deploy_key` 私钥或服务器密码）→ 执行 `bash /srv/quju/main/deploy/deploy.sh`。

### ❓ 你问"我需要提供什么"——三选一
- **你自己在 CodeArts 网页点配置**（推荐，最稳）：什么都不用给我，照上面 1–3 做即可；我可远程帮你把"服务器侧 deploy.sh / 首次 clone"跑通。
- **我用 CodeArts OpenAPI 帮你自动建流水线/主机/触发**：给我 华为云 **IAM AK/SK**(含 CodeArts 权限) + **region** `cn-north-4` + **CodeArts 项目 ID**（请确认 URL 里 `5c0917…` 是项目还是仓库 ID）。
- **退路：方案 B（Webhook→服务器）**：无需 CodeArts 主机配置，我在服务器起一个 webhook 监听器，你在仓库 Webhook 填地址即可。
