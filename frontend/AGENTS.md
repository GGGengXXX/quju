# frontend/AGENTS.md — 前端规范（Vue 3 + TypeScript）

> 继承根 `../AGENTS.md` 的所有铁律与全局约定。本文件只讲前端层的具体规范。

## 技术栈

- Vue 3（`<script setup>` + Composition API）、TypeScript、Vite、Pinia、Vue Router、Element Plus、Axios。
- 安装：`npm ci`；开发：`npm run dev`；类型检查：`npm run type-check`；构建：`npm run build`；lint：`npm run lint`。
- `<<TODO: 确认组件库（Element Plus / Arco / Naive）、包管理器（npm/pnpm）、地图 SDK（高德 JS API）>>`

## 目录结构（按"功能模块"组织）

```
src/
├─ api/
│   ├─ generated/   由 OpenAPI 生成的类型 + client（勿手改，已 gitignore）
│   └─ http.ts       axios 实例：注入 token、统一解信封、错误码处理
├─ stores/          Pinia（auth、user、各模块）
├─ router/
├─ components/      跨模块通用组件
├─ composables/     useXxx 复用逻辑
├─ views/
│   ├─ user/  activity/  social/  team/  chat/  admin/   ← 按模块，你只改你负责的
│   └─ ...
└─ utils/
```
> 你只改你负责模块的 `views/<module>/` 与对应 store/api 封装（见 `docs/work-assignment.md`）。`api/http.ts`、`router`、全局 `components` 改动需告知/评审。

## 硬性约定

- **只通过生成的 client 调后端**：所有请求走 `api/generated` + `api/http.ts`，**不要**手写 URL 字符串、不要散落 axios 调用。后端契约变了就重新生成。
- **统一解信封**：`http.ts` 统一处理 `{ code, message, data }`：`code=0` 取 `data`；非 0 按 `error-codes.md` 提示/跳转（如 1001 跳登录）。业务组件拿到的就是 `data`。
- **TypeScript 严格**：`strict: true`，不滥用 `any`；接口类型来自生成的类型，不手抄。
- **状态**：登录态/用户信息/全局态用 Pinia；组件内局部态用 `ref/reactive`。token 存储与刷新统一在 auth store。
- **组件**：`<script setup lang="ts">`；props/emits 显式类型；样式 scoped；命名 PascalCase 文件、kebab-case 标签。
- **路由守卫**：未登录访问受保护页 → 跳登录；按 userType / 角色控制可见入口。
- **地图**：选点/附近/地图模式统一封装成 `composables/useMap.ts`，key 走环境变量。`<<TODO: 高德 key>>`
- **配置/密钥**：`.env`（已 gitignore），`VITE_` 前缀；不提交 key。

## 契约未就绪也能开工

后端没写完时，用 `make mock`（contracts/）起的 mock server 联调；前端进度不被后端阻塞。

## 测试 & 性能

- 关键逻辑/组件写单测（Vitest）；`type-check` + `lint` + `test` 全绿才算完成（DoD）。
- 首屏 < 5s：路由懒加载、按需引入组件库、图片懒加载、长列表虚拟滚动。
