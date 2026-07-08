# ADR 0007：聊天内 AI 助手回复（上下文建议回复 + 用户级 prompt 设置）

- 状态：提议
- 日期：2026-07-08
- 决策者：平台核心组（待评审）

## 背景
扩展需求要求在私聊和群聊中提供一个低摩擦的 AI 回复助手：用户可以在聊天过程中一键或通过快捷键唤起 AI，基于当前会话上下文生成一条建议回复草稿；同时，用户希望在设置中配置自己的 system prompt，以便控制回复风格。

当前仓库已有：
- `GET /v1/messages`：按会话读取历史消息
- `POST /v1/messages`：发送消息
- `GET/PUT /v1/users/me`：读取与修改用户资料
- `module/activity/service/ActivityAiService`：已有 OpenAI 兼容 AI 调用范式

但当前契约中不存在：
- 聊天 AI 建议回复端点
- 用户级 AI 助手设置字段

因此，这个功能不能绕过 `contracts/` 直接实现，必须先通过 ADR 明确契约与边界。

## 决策
1. 新增聊天 AI 建议回复端点：
   ```yaml
   POST /v1/messages/ai-reply
   ```
   请求参数至少包含：
   - `scope`: `FRIEND | TEAM`
   - `peerId`: 会话对端用户 ID 或 teamId

   响应返回：
   - `suggestion`: AI 生成的建议回复文本
   - `contextCount`: 实际使用的上下文消息条数

2. 扩展用户资料 schema，新增用户级 AI 设置：
   ```yaml
   aiSettings:
     systemPrompt: string
   ```
   第一版只提供 `systemPrompt`，快捷键本身由前端固定，不写入契约。

3. 第一版不新增数据库表，用户 AI 设置先复用 `user.privacy_settings` JSON 存储，后端以兼容方式解析，避免扩大 DDL 变更面。

4. AI 回复模式采用“生成草稿，不自动发送”：
   - 后端返回建议文本
   - 前端写入输入框，由用户决定是否发送

5. 上下文范围限定为“当前用户有权访问的当前会话最近 N 条消息”，严禁跨会话拼接。

## 后果（好处 / 代价 / 影响谁）
- 好处：
  - AI Native 价值直接落在聊天主流程中，入口足够轻。
  - 不让 AI 自动发消息，降低误发和越权风险。
  - 用户可自定义回复风格，体验更贴近真实需求。
- 代价：
  - 需要新增 OpenAPI 端点和用户资料字段语义。
  - `privacy_settings` 解析逻辑要从“仅布尔隐私项”扩展为“布尔隐私项 + AI 设置混合结构”。
- 影响谁：
  - 后端 `module/social`、`module/user`
  - 前端 `ChatView`、`Profile`
  - 契约维护由平台核心组审批

## 备选方案
1. 让 AI 自动发送消息：交互最省一步，但误发风险高，且不利于社交安全，否决。
2. 后台统一配置全平台 prompt：运维集中，但用户无法自定义风格，且后台改动更大，第一版不选。
3. 为 AI 会话单独建表保存多轮上下文：可扩展性更好，但开发面过大，不适合几个小时内的增量开发，第一版不选。

## 实施备注
- 合同路径上，建议先单独 MR 修改 `contracts/`：
  - 新增 `/v1/messages/ai-reply`
  - 扩展 `UserVO` / `UpdateProfileReq` 的 `aiSettings`
- 契约 MR 获批后，再进入代码实现 MR。
