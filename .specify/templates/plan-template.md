# 实现计划 Plan：<功能名>

> 由 AI 基于 spec + 契约 + 现有代码模式生成；人确认后再进入 tasks/implement。

## 1. 方案概述
一段话说明实现思路，引用要遵循的现有模式（指向具体文件）。

## 2. 后端改动
- 包/文件：`backend/.../module/<x>/...`（controller / service / mapper / entity / dto）
- 数据库：迁移脚本 `db/migration/Vxxx__...sql`
- 关键逻辑 / 事务 / 校验 / 错误码

## 3. 前端改动
- 视图/组件：`frontend/src/views/<module>/...`
- store / api 封装；路由

## 4. 契约依赖
- 用到的端点（已存在 / 需补充）；用到的生成类型
- 是否需改契约（是 → 先走 ADR）

## 5. 测试策略
- 后端：哪些 service/接口测；前端：哪些组件/逻辑测
- 端到端验证脚本/步骤

## 6. 风险与依赖
- 依赖别人的接口/表？第三方（地图/AI/邮件）？回退方案？
