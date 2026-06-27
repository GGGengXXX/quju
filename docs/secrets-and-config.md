# 密钥与配置说明（重要：任何密钥都不入库）

> 本文件只说明**每个密钥从哪来、用哪个环境变量引用**；**不写真实值**。
> 真实值放在：开发期各 worktree 的 `.env`（已 gitignore）；部署期服务器的 `deploy/.env`（仅服务器本地，不入库）。
> 模板见仓库根 `.env.example`。

## 配置清单

| 用途 | 环境变量 | 真实值来源 | 备注 |
|---|---|---|---|
| 阿里云 OSS AccessKey ID | `OSS_ACCESS_KEY_ID` | `AccessKey.csv` 第 1 列 | 阿里云 RAM 密钥，**勿入库** |
| 阿里云 OSS AccessKey Secret | `OSS_ACCESS_KEY_SECRET` | `AccessKey.csv` 第 2 列 | **勿入库** |
| OSS Bucket | `OSS_BUCKET` | 固定 `se-resource-bucket` | 非密钥，可写文档 |
| OSS Endpoint | `OSS_ENDPOINT` | `oss-cn-beijing.aliyuncs.com` | 北京地域 |
| 部署服务器 IP | `DEPLOY_HOST` | `HuaweiCloud.txt` `ip:` | `1.92.124.5` |
| 部署服务器密码 | `DEPLOY_PASSWORD` | `HuaweiCloud.txt` `password:` | **勿入库**；强烈建议尽快改用 SSH 密钥登录并改强密码 |
| CodeArts 仓库 | `REPO_URL` | 文档固定值 | 非密钥 |
| 数据库密码（app 账号 quju） | `DB_PASSWORD` | 服务器 `/root/quju/mysql.env` 的 `MYSQL_PASSWORD` | **勿入库** |
| 数据库 root 密码 | — | 服务器 `/root/quju/mysql.env` 的 `MYSQL_ROOT_PASSWORD` | 仅运维用，**勿入库** |
| JWT 签名密钥 | `JWT_SECRET` | 各环境随机生成 | **勿入库** |
| AI 服务 key | `AI_API_KEY` | `deepseek-api-key.txt` | DeepSeek，OpenAI 兼容 |
| AI base_url | `AI_BASE_URL` | `https://hk.n1n.ai/v1` | OpenAI 兼容端点 |
| AI 默认模型 | `AI_MODEL` | `deepseek-v3.2` | 活动策划/审核/图片分类默认模型 |
| 高德地图 key | `VITE_AMAP_KEY` | `高德地图-api-key.txt` | 选点/附近/距离/地图模式（前端构建期注入） |
| 邮件 SMTP 主机/端口 | `SMTP_HOST` / `SMTP_PORT` | `smtp.163.com` / `465`(SSL) | 网易 163 |
| 邮件 SMTP 账号 | `SMTP_USERNAME` / `MAIL_FROM` | `tluvx0806@163.com` | 发件邮箱 |
| 邮件 SMTP 密码 | `SMTP_PASSWORD` | `邮箱验证配置信息.txt` 的授权码 | **勿入库**；163 授权码非登录密码 |
| 短信服务 | `SMS_*` | `<<TODO: 待提供>>` | 需求中第三方 |

## 阿里云 OSS（Java 后端接入要点）
- 用阿里云 OSS Java SDK（`com.aliyun.oss:aliyun-sdk-oss`）。
- 封装一个 `OssService`（在 `backend/.../common` 或 `module` 的存储适配里），统一上传/签名 URL；
  Endpoint/Bucket/Key 全部从上面的环境变量读，**不要写死**。
- 用途：头像、活动图片、群文件、小队相册、活动总结图片。
- 建议：上传走后端签名直传或后端中转；公开读资源用 OSS 的「读权限」或带签名的临时 URL。

## 安全提醒
- `AccessKey.csv`、`HuaweiCloud.txt` 等凭证文件**不要拷进本仓库**（`.gitignore` 已按名拦截）。
- 服务器密码是弱口令（`HuaweiCloud.txt`），上线前务必改强密码 + 配置 SSH 密钥 + 关闭密码登录。
- 阿里云 AccessKey 尽量用 **RAM 子账号**且只授予 OSS 该 bucket 的最小权限。
