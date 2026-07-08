# 实现计划 Plan：聊天内 AI 助手回复

## 1. 方案概述
第一版采用“建议回复草稿”模式，不让 AI 自动发送消息。后端在 `module/social` 内新增 AI 回复服务，复用现有消息查询、好友/群成员权限判断和 AI HTTP 调用模式；前端在聊天输入区增加一个低摩擦 AI 入口，并把生成结果写回输入框。个人 AI 提示词不新建表，先复用 `user.privacy_settings` JSON 存储，降低变更面。

## 2. 规范流程与前置条件
- 本功能会新增聊天 AI 回复端点，并扩展用户资料 schema 语义，属于契约变更。
- 按根 [AGENTS.md](/Users/ggengx/Documents/BUAA-xxq/quju/AGENTS.md) 金律 #1，必须先：
  1. 写 ADR
  2. 单独修改 `contracts/`
  3. 由平台核心组评审通过
- 在契约未批准前，本分支可先完成 spec / ADR / 实现计划，代码实现需等待契约路径确认后再继续。

## 3. 后端改动
- 包/文件：
  - `backend/src/main/java/cn/edu/buaa/quju/module/social/controller/MessageController.java`
  - `backend/src/main/java/cn/edu/buaa/quju/module/social/service/MessageService.java`
  - 新增 `ChatAiService` 或在 `module/social/service` 中拆出专用 AI 服务
  - `backend/src/main/java/cn/edu/buaa/quju/module/social/dto/SocialDtos.java`
  - `backend/src/main/java/cn/edu/buaa/quju/module/user/{dto,service}`
- 关键逻辑：
  - 校验用户对目标会话的访问权限
  - 拉取最近 N 条消息构造上下文
  - 拼接服务端 system prompt + 用户自定义 prompt + 会话上下文 + 当前任务指令
  - 调用 AI 服务生成建议回复
  - 限制生成文本长度，空结果和异常统一兜底
  - 用户 AI 设置从 `privacy_settings` JSON 读写

## 4. 前端改动
- `frontend/src/views/social/ChatView.vue`
  - 新增 AI 助手按钮
  - 增加快捷键触发
  - 生成中 loading、失败提示、成功回填输入框
- `frontend/src/views/Profile.vue`
  - 新增 AI 助手设置区域
- `frontend/src/api/social.ts`、`frontend/src/api/auth.ts`
  - 新增 AI 回复接口与 `aiSettings` 类型

## 5. 测试策略
- 后端：
  - `MockMvc` 测试发起 AI 回复请求的成功路径
  - 无权限访问失败路径
  - 用户设置保存/读取路径
  - AI 异常兜底路径
- 前端：
  - 现有仓库未配置单测脚本，至少跑 `npm run type-check` 与 `npm run build`
- 对抗式自审：
  - 对照 spec 审查“是否自动发送”“是否跨会话泄漏上下文”“是否暴露内部 prompt”

## 6. 风险与边界
- `privacy_settings` 当前类型偏向布尔隐私项；第一版需谨慎向后兼容解析逻辑。
- 快捷键设计不能与浏览器常用输入快捷键强冲突；建议使用 `Meta/Ctrl + Shift + Enter`。
- AI 回复是第三方调用，需接受响应时间波动，前端必须有明确加载态与错误提示。
