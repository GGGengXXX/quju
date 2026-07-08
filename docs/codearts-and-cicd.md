# GitHub：仓库 / 代码推送 / CI-CD（服务器自托管部署）

> 代码托管在 **GitHub**。CI/CD 由 **GitHub Actions + 服务器部署脚本** 组成：
> 服务器上开发 → push → Pull Request 合并 → GitHub Actions 自动测试 / 部署。
> 参考：[GitHub Actions 文档](https://docs.github.com/actions)

| 资源 | 值 |
|---|---|
| 仓库地址 | `https://github.com/GGGengXXX/quju.git` |
| 平台 | GitHub |
| 部署服务器 | `1.92.124.5`（密码见 `HuaweiCloud.txt`，**不入库**；详见 `secrets-and-config.md`） |

---

## 〇、分支模型（master / dev / feature）

- **`master`**：主分支 / 生产分支，**受保护**。**合并到 master = 触发服务器自动部署**。
- **`dev`**：集成分支，所有 feature 先合并到这里联调。
- **feature 分支**：从 `dev` 拉出（如 `feat/activity-map`），开发完 MR 合并回 `dev`。
- **部署**：联调 OK 后把 `dev` 合并到 `master`（PR）→ GitHub Actions 触发部署 → 服务器 `deploy/deploy.sh` 拉 `master` 重新部署。

`feature ──MR──▶ dev ──MR(部署)──▶ master ──触发──▶ 服务器自动部署`

> `dev-bootstrap.sh` 默认从 `origin/dev` 开 feature；`deploy/deploy.sh` 默认部署 `master`（可 `DEPLOY_BRANCH` 覆盖）。

---

## 一、仓库创建（已存在，下面是从零的方法 + 首推）

仓库已创建好（URL 见上）。若需新建：GitHub → New repository → 名称 `quju` → Public/Private 按需选择 → 不勾选初始化 README（我们要 push 现有工作区）。

### 把本工作区首次推送上去

```bash
cd quju
git init -b master
git add .
git commit -m "chore: 初始化工作区"
git remote add origin https://github.com/GGGengXXX/quju.git
git push -u origin master && git push origin master:dev
```

**代码推送鉴权（默认 HTTPS；部署走 SSH）**：
- **HTTPS（默认，用于代码上传/push）**：使用 GitHub Personal Access Token。
- **SSH（推荐）**：把个人 `~/.ssh/id_ed25519.pub` 加到 GitHub SSH Keys；remote 用 `git@github.com:GGGengXXX/quju.git`。
- **协议可配置**：由 remote URL 决定；脚本用 `QUJU_REPO_URL` 环境变量切换（默认 HTTPS，偏好 SSH 者改为 `git@…` 形式）。

---

## 二、代码推送 + Pull Request 流程

GitHub 日常（在你的 clone 内）：

```bash
git push -u origin feat/<module>-<slug>
```
然后在 GitHub 网页新建 PR：源分支 = 你的 feature 分支，**目标 = `dev`**，描述粘贴 `docs/merge-request-template.md` 或 `.github/pull_request_template.md`。（部署时再 `dev`→`master`。）

### 用分支保护 + 评审来落地“契约即法律”

GitHub 可直接用分支保护 + CODEOWNERS/评审规则达到同等约束：

1. **保护 `master`/`dev` 分支**：禁止直接 push、要求 PR、要求 CI 通过。
2. **平台核心组设为必选评审人**：至少 1 名平台核心组成员批准后才能合并。
3. **约定**：改 `contracts/` 必须**单独 PR** + 先有 ADR；平台核心组在该 PR 上把关。
4. （可选）补 `CODEOWNERS`，对 `contracts/` 与 `docs/adr/` 指定核心组。

---

## 三、CI/CD（GitHub Actions：合并到 master → 自动部署）

CI/CD = **GitHub Actions**：

- **CI 触发**：对 `master`、`dev`、`feat/**`、`fix/**` 的 push / PR。
- **CI 内容**：契约校验、后端 `mvn test`、前端 `npm ci && npm run type-check && npm run build`。
- **部署触发**：合并到 `master`。
- **部署任务**：GitHub Actions 通过 SSH 登录服务器，执行 `bash /srv/quju/main/deploy/deploy.sh`。

服务器侧全部就绪（`deploy.sh` / docker 编排 / MySQL），详见 `deploy/README.md`。
日常：feature →(PR)→ `dev` 联调 →(PR)→ `master` → 自动部署到 http://1.92.124.5 。

运行时拓扑：`nginx(:80) → /v1/* 反代 backend:8080 → mysql:3306(quju) + redis:6379`（quju-net 内；OSS 走外部阿里云）。
