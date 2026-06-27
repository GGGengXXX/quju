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
git init -b master
git add .
git commit -m "chore: 初始化工作区"
git remote add origin https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git
git push -u origin master && git push origin master:dev
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

1. **保护 `master`/`dev` 分支**：仓库 → 设置 → 分支管理/保护分支 → 设为受保护：禁止直接 push、必须经 MR、要求 ≥1 通过评审。
2. **平台核心组设为必选评审人**：仓库 → 设置 → 合并请求/评审规则 → 添加平台核心 2 人为必需评审者（或建评审组）。
3. **约定**：改 `contracts/` 必须**单独 MR** + 先有 ADR；平台核心组在该 MR 上把关。MR 模板里有自检项强制声明"是否动了 contracts/"。
4. （可选，若你们版本支持）「评审模板」「门禁(Gate)」：把测试/契约校验设为合并门禁。

---

## 三、CI/CD（已建好：合并到 master → 自动部署）

CI/CD = **CodeArts 流水线**，已在 UI 配好并验证通过：

- **触发**：代码提交 / 合并到 `master`。
- **部署任务**：CodeArts Deploy 主机组（主机 `1.92.124.5`，root:22，SSH 认证 = 服务器上 `/root/quju/codearts_deploy_key` 私钥）→ 动作「执行 Shell 命令」：`bash /srv/quju/main/deploy/deploy.sh`（拉 master → `docker compose up -d --build` 滚动重启）。
- ⚠️ **关键坑**：必须用**能选主机组的「部署」动作**；流水线里通用的「执行shell」跑在 CodeArts 云端容器（找不到 `/srv/quju/main`，报 `No such file`）。

服务器侧全部就绪（部署密钥 / `deploy.sh` / docker 编排 / MySQL），详见 `deploy/README.md`。
日常：feature →(MR)→ `dev` 联调 →(MR)→ `master` → 自动部署到 http://1.92.124.5 。

运行时拓扑：`nginx(:80) → /v1/* 反代 backend:8080 → mysql:3306(quju) + redis:6379`（quju-net 内；OSS 走外部阿里云）。
