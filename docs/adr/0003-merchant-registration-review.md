# ADR 0003：商家注册接入后台审核流程（RegisterRequest 增加 merchantName）

- 状态：提议
- 日期：2026-07-03
- 决策者：平台核心组（待评审）

## 背景
需求 v2 §1.1：「商家需要上传营业执照或营业凭证，后台审核通过后获得商家身份」。

现状实现存在缺口：
- `POST /auth/register` 对 `userType=MERCHANT` 仅校验 `licenseUrl` 非空，但**并不持久化**该营业执照，也**不创建** `merchant_profile`、**不进入任何审核**——商家激活后即为 `MERCHANT`，审核逻辑为空。
- 只有已注册用户走 `POST /merchants/apply` 才会生成 `merchant_profile(PENDING)`。直接注册为商家的用户绕过了审核。

要让「商家注册 → 邮箱激活 → 后台审核」闭环，注册时必须携带足以建档的商家信息。`merchant_profile.merchant_name` 为 `NOT NULL`，而 `RegisterRequest` 目前无 `merchantName`，因此需要新增该字段——属契约变更，按金律 #1 走 ADR。

## 决策
1. `RegisterRequest` 增加可选字段 `merchantName`；当 `userType=MERCHANT` 时，`merchantName` 与 `licenseUrl` 均必填。个人注册不受影响。

   ```yaml
   RegisterRequest:
     properties:
       # …既有 email/password/userType/licenseUrl 不变…
       merchantName: { type: string, description: 'userType=MERCHANT 时必填(商家名称)' }
   ```

2. 后端注册流程（`AuthService.register`）：商家注册时创建 `user(userType=MERCHANT, status=PENDING_ACTIVATION)`，并创建 `merchant_profile(audit_status=PENDING, merchant_name, license_url)`，把营业执照落库、进入后台审核队列。激活后即可登录并看到「审核中」状态。

3. 「商家身份」的**认证态**由 `merchant_profile.audit_status` 表达；`userType=MERCHANT` 表示「注册为商家账号」。后台审核（`AdminUserService.reviewMerchant`）：
   - 通过 → `audit_status=APPROVED`，并确保 `userType=MERCHANT`（兼容个人用户经 `/merchants/apply` 升级的场景）。
   - 驳回 → `audit_status=REJECTED`（填原因），**不改动 userType**（商家可重新提交；个人升级被拒则保持 INDIVIDUAL）。

## 后果（好处 / 代价 / 影响谁）
- 好处：补齐需求 §1.1 的商家审核闭环；营业执照不再丢失；直接注册的商家也纳入人工审核。
- 代价：契约新增一个可选字段；注册前端需在选择「商家」时多填商家名称。向后兼容（个人注册不变）。
- 影响谁：`module/user`（注册/商家资料）、`module/admin`（审核，已实现）、前端注册页（用户+后台组）；契约由平台核心组合入。

## 备选方案
1. 注册不带商家信息，激活后强制跳转 `/merchants/apply` 补全再进审核：多一步且激活到申请之间状态含糊，体验差，否决。
2. `merchant_profile.merchant_name` 改可空、注册用占位名：脏数据 + 后台审核看不到真实商家名，否决。
3. 本 ADR：注册即带 `merchantName`、落库 `merchant_profile(PENDING)`（推荐）。
