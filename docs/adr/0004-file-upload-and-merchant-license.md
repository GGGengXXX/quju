# ADR 0004：文件上传端点入契约 + 营业执照图片上传与后台审核展示

- 状态：提议
- 日期：2026-07-03
- 决策者：平台核心组（待评审）

## 背景
需求 v2 §1.1：「商家需要上传营业执照或营业凭证，后台审核通过后获得商家身份」；§4.2：管理员审核商家时需查看其基本信息。

现状缺口：
- **营业执照仍是手输 URL**。注册页 / 商家资料页让用户直接粘贴图片 URL，而非选择本地图片上传，不符合「上传营业执照」的语义，实操也几乎无法完成。
- **上传能力已存在但未入契约**。后端早有 `POST /v1/upload/image`（走 OSS，返回 `{ url }`），头像、活动总结图等均在用；但 `contracts/openapi.yaml` **完全没有** `/upload/*` 端点。前端要接营业执照上传，只能依赖未登记的接口——属契约缺口，按金律 #1 应补 ADR + 契约。
- **注册阶段无法复用现有上传**。`POST /upload/image` 内部 `UserContext.require()` 强制登录，而营业执照要在**注册（未登录）**时就上传，因此需要一个匿名可用的上传端点。
- **后台审核缺少可展示信息**。`GET /admin/merchant-applications` 当前响应为无类型 `Ok`，未约定返回营业执照、商家昵称、关注领域等字段，前端无契约依据展示「商家相关信息」。

已确认**无需**变更的部分（现有契约已覆盖，属实现补齐而非契约改动）：
- `RegisterRequest` 已含 `licenseUrl`、`merchantName`。
- `MerchantApplyRequest`（`PUT /merchants/me` 复用）已含 `licenseUrl`；此前后端 `MerchantUpdateReq` 未读取该字段，本次补齐后即与契约一致（DoD：行为与 openapi 完全一致）。

## 决策
1. **将文件上传端点正式纳入契约**，新增 `upload` tag，登记两个端点：

   ```yaml
   /upload/image:
     post:
       tags: [upload]
       summary: 上传图片（头像/活动图等，需登录）
       requestBody:
         required: true
         content:
           multipart/form-data:
             schema: { type: object, required: [file], properties: { file: { type: string, format: binary } } }
       responses: { '200': { $ref: '#/components/responses/UploadResp' } }
   /upload/license:
     post:
       tags: [upload]
       summary: 上传营业执照/凭证（注册未登录亦可用）
       security: []           # 匿名可访问：注册阶段尚无 token
       requestBody:
         required: true
         content:
           multipart/form-data:
             schema: { type: object, required: [file], properties: { file: { type: string, format: binary } } }
       responses: { '200': { $ref: '#/components/responses/UploadResp' } }
   ```

   ```yaml
   # components.responses
   UploadResp:
     description: 上传成功，返回可访问 URL
     content:
       application/json:
         schema:
           allOf:
             - $ref: '#/components/schemas/ApiResponse'
             - type: object
               properties:
                 data: { type: object, properties: { url: { type: string } } }
   ```

2. **前端营业执照改为图片选择上传**：注册页、商家资料页由「URL 文本框」改为「选择图片 → 调 `/upload/license` → 存返回 URL」，带缩略图预览。商家资料页更换执照时后端将 `audit_status` 重置为 `PENDING` 重新送审。

3. **后台审核端点约定类型化响应**，登记 `MerchantApplication` schema，明确回传营业执照及商家信息，供管理员审核时展示（含执照大图预览）：

   ```yaml
   MerchantApplication:
     type: object
     properties:
       id: { type: integer, format: int64 }
       userId: { type: integer, format: int64 }
       merchantName: { type: string }
       nickname: { type: string }
       focusFields: { type: string }
       licenseUrl: { type: string }
       auditStatus: { $ref: '#/components/schemas/AuditStatus' }
       auditReason: { type: string }
       createdAt: { type: string, format: date-time }
   ```

   `GET /admin/merchant-applications` 的 `200` 响应由 `Ok` 改为 `PageResult<MerchantApplication>`。（后端 `MerchantAppVO` 已补 `nickname`、`focusFields`，回传 `licenseUrl`。）

## 后果（好处 / 代价 / 影响谁）
- 好处：营业执照真正「上传」；上传能力入契约不再是隐形依赖；后台审核有契约保证的信息（含执照图）可展示，闭合需求 §1.1 / §4.2。
- 代价：契约新增 `upload` tag 两个端点 + `UploadResp` / `MerchantApplication` 两个组件；`/upload/license` 匿名可访问，需在实现层加**文件类型/大小校验与限流**防滥用（见备选方案 3 与实现约束）。
- 影响谁：`module/user`（注册/商家资料）、`module/admin`（审核列表）、`common/UploadController`、前端注册页/资料页/后台商家审核页；契约由平台核心组合入。向后兼容（个人注册与既有 `/upload/image` 行为不变）。

## 实现约束
- `/upload/license` 必须校验 MIME 为 `image/*`、限制单文件大小（建议 ≤ 5MB），并对匿名调用限流，避免图床被刷。
- 匿名上传对象归入 `merchant/license/anon` 前缀，登录用户归入 `merchant/license/{userId}`，便于审计与清理。

## 备选方案
1. 继续手输 URL：不满足「上传」语义、几乎不可用，否决。
2. 复用 `POST /upload/image` 并放开其登录校验：会让所有图片上传变匿名，扩大攻击面，否决；改为单独的匿名 `/upload/license` 端点，最小化暴露。
3. 注册阶段不传执照、激活后再上传：注册到审核之间状态含糊、体验差（与 ADR 0003 同类权衡），否决。
4. 本 ADR：上传端点入契约 + 独立匿名 `/upload/license` + 类型化审核响应（推荐）。
