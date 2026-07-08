# 任务清单 Tasks：聊天内 AI 助手回复

| # | 任务 | 依赖 | 验证方式 | 状态 |
|---|---|---|---|---|
| 1 | 完成 spec 与 ADR，确认契约变更范围 | — | 文档齐备 | ☑ |
| 2 | 提交契约变更 MR：新增 AI 回复端点与用户 `aiSettings` 字段 | 1 | `make lint-contract` | ☐ |
| 3 | 后端实现用户 AI 设置读写与向后兼容解析 | 2 | `mvn test` | ☐ |
| 4 | 后端实现聊天 AI 回复接口、权限校验、上下文裁剪、AI 调用兜底 | 2,3 | `mvn test` | ☐ |
| 5 | 增加后端自动化测试覆盖成功/越权/异常路径 | 4 | `mvn test` | ☐ |
| 6 | 前端实现聊天页 AI 按钮、快捷键、加载态与草稿回填 | 2 | `npm run type-check && npm run build` | ☐ |
| 7 | 前端实现 Profile AI 设置表单与保存逻辑 | 2 | `npm run type-check && npm run build` | ☐ |
| 8 | 联调并做对抗式自审 | 5,6,7 | `git diff --check` + 走查 spec | ☐ |
| 9 | 准备 MR 说明与部署验证步骤 | 8 | 验证记录 | ☐ |
