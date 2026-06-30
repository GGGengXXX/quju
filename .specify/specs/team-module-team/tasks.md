# 任务清单 Tasks：R4 兴趣小队模块

| # | 任务 | 依赖 | 验证方式 | 状态 |
|---|---|---|---|---|
| 1 | 确认 R4 契约、错误码、枚举和允许改动边界 | — | spec/plan 完成 | ☑ |
| 2 | 搭建后端 team DTO、controller 与 service 骨架 | 1 | Docker `mvn -q -DskipTests compile` | ☑ |
| 3 | 实现小队创建、发现、详情、加入、审批、离队、解散、成员权限 | 2 | Docker `mvn test` | ☑ |
| 4 | 实现公告、投票、文件、相册、动态、积分榜、队内活动接口 | 3 | Docker `mvn test` | ☑ |
| 5 | 补充后端测试覆盖核心分支 | 3,4 | Docker `mvn test` | ☑ |
| 6 | 新增前端 team API、路由和工作台页面 | 1 | Docker `npm run type-check && npm run build` | ☑ |
| 7 | 联调并检查搜索、加入、审批、管理交互 | 5,6 | 代码路径 + UI 交互自检 | ☑ |
| 8 | 对抗式自审并修复问题 | 7 | `git diff --check` | ☑ |
| 9 | 汇总验证证据，准备 MR 说明 | 8 | `git status` + 验证记录 | ☑ |
