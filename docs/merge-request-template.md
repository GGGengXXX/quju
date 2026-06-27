<!--
CodeArts 合并请求(MR)描述模板。
用法：在 CodeArts 仓库 设置 → 合并请求 中设为默认模板；或新建 MR 时把本段粘进描述。
MR 标题格式：<module>: <做了什么>，如  activity: 实现地图模式附近活动查询
-->

## 关联
- 需求条目：R<编号>（见 docs/requirements-breakdown.md）
- Spec：`.specify/specs/<feature>/spec.md`

## 改动概述
<一句话说明这个 MR 做了什么>

## 涉及的契约端点 / 表
- 端点：`<METHOD /v1/...>`
- 表/迁移：`<表名 / Vxxx__...sql>`
- [ ] **未改动 `contracts/`**；或 → 已有 ADR：`docs/adr/____` 且平台核心组已在本 MR 评审

## Definition of Done 自检
- [ ] 行为与 `contracts/openapi.yaml` 一致（字段/类型/错误码）
- [ ] 只改了我负责的目录/包/表
- [ ] 写了测试且本地通过：后端 `mvn test` / 前端 `type-check`+`lint`+`test`
- [ ] 过了一次对抗式自审，无遗留
- [ ] 非功能：相关接口 < 2000ms / 列表分页 / 地理查询走空间索引（如适用）

## 验证证据
<贴测试输出 / 截图 / 手测步骤结果>
