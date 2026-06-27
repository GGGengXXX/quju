# backend/AGENTS.md — 后端规范（Spring Boot + MyBatis-Plus）

> 继承根 `../AGENTS.md` 的所有铁律与全局约定。本文件只讲后端层的具体规范。

## 技术栈

- Java 17、Spring Boot 3.x、MyBatis-Plus、MySQL 8、Redis、Maven。
- 构建：`mvn -q -DskipTests package`；测试：`mvn test`；本地起服务：`mvn spring-boot:run`。
- `<<TODO: 平台核心组确认 Spring Boot 小版本、Java 版本、是否引入 Spring Security>>`

## 分包结构（按"功能模块"分包，不是按层全局分）

```
cn.edu.buaa.quju
├─ common/        统一信封、全局异常处理、分页、JWT、基类、工具
├─ config/        Spring 配置（MyBatis-Plus、Redis、CORS、Web、Swagger…）
├─ generated/     由 OpenAPI 生成的接口/DTO（勿手改，已 gitignore）
└─ module/
    ├─ user/      controller / service / mapper / entity / dto / converter
    ├─ activity/
    ├─ social/
    ├─ team/
    ├─ chat/
    └─ admin/
```
> 每个 `module/<x>/` 内分 `controller service mapper entity dto`。**你只改你负责的 module 包**（见 `docs/work-assignment.md`）。`common/` `config/` 归平台核心组，改动需评审。

## 硬性约定

- **Controller 只做编排**：参数校验（`@Valid`）+ 调 service + 返回 `R.ok(data)`。不写业务逻辑。
- **统一返回 `R<T>`**：`common` 提供 `R.ok(data)` / `R.fail(code, msg)`，对应根 AGENTS §3 的信封。Controller 返回 `R<T>`，**不要**返回裸实体。
- **全局异常处理**：`@RestControllerAdvice` 统一把业务异常 `BizException(code,msg)` 转成信封。业务里直接 `throw new BizException(ErrorCode.XXX)`，不在 controller 散落 try/catch。
- **DTO ≠ Entity**：对外用 DTO（来自/对齐 OpenAPI），DB 用 Entity；用 MapStruct 或手写 converter 转换。**绝不把 Entity 直接暴露给前端。**
- **MyBatis-Plus**：简单 CRUD 用 `BaseMapper`/`IService`；复杂查询写 XML 或 `QueryWrapper`。分页用 MyBatis-Plus 分页插件，返回根约定的 `PageResult`。
- **空间查询**（附近/距离/地图）：用 MySQL `ST_Distance_Sphere` + `SPATIAL INDEX`，**不要**全表扫描后内存算距离。
- **事务**：跨表写操作加 `@Transactional`，注意自调用失效。
- **鉴权**：受保护接口从 JWT 取 `userId`（统一通过 `@CurrentUser` 或拦截器注入），不要从前端传的字段信任身份。
- **配置/密钥**：走 `application.yml` + 环境变量；第三方 key 不写死、不提交。
- **文件存储（阿里云 OSS）**：头像/活动图/群文件/相册/总结图统一走一个 `OssService` 适配器（阿里云 OSS Java SDK `com.aliyun.oss:aliyun-sdk-oss`），bucket=`se-resource-bucket`，Endpoint/Key 全部读环境变量（见 `docs/secrets-and-config.md`）。**禁止**在业务代码里散落 OSS client 或写死 key。上传建议后端签名/中转，公开资源用临时签名 URL。
- **迁移**：DB 变更通过 Flyway/Liquibase 脚本（`src/main/resources/db/migration/`），基线来自 `contracts/schema.dbml`。**不手改线上库**。`<<TODO: 确认 Flyway 还是 Liquibase>>`

## 测试

- service 层单测覆盖核心分支；关键接口写 `@SpringBootTest` + MockMvc 集成测试。
- 用 Testcontainers 或 H2 起隔离 DB（`<<TODO: 确认>>`）；不要依赖别人的开发库数据。
- 写完跑 `mvn test`，全绿才算完成（DoD）。

## 性能（对齐非功能指标 <2000ms）

- 列表必分页；避免 N+1（必要时手写 join / 批查）；热点读走 Redis。
- 慢点优先：地理查询、首页信息流、搜索。
