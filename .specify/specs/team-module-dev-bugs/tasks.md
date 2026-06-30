# 任务清单 Tasks：R4 小队模块 dev 缺陷修复

| # | 任务 | 依赖 | 验证方式 | 状态 |
|---|---|---|---|---|
| 1 | 补本次 bugfix 的 spec / plan / tasks | — | 文档落盘 | ☑ |
| 2 | 修正小队列表按钮和详情加载守卫 | 1 | 页面手查 + type-check | ☑ |
| 3 | 为相册/动态图片补 URL 规范化和展示修正 | 2 | type-check + build | ☑ |
| 4 | 实现公告 @ 相关提醒轮询与弹窗 | 2 | type-check + build | ☑ |
| 5 | 运行前端验证并整理结果 | 3,4 | `npm run type-check && npm run build` | ☑ |
