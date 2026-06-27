# 趣聚 QuJu —— 契约代码生成 / 工具
# 依赖 npx（Node）；首次运行自动下载工具。生成产物在 gitignore 内，勿手改。
OPENAPI = contracts/openapi.yaml

.PHONY: help lint-contract gen-frontend gen-backend mock db-apply

help:
	@echo "make lint-contract  # 校验 OpenAPI 契约"
	@echo "make gen-frontend   # 生成前端 TS 类型 -> frontend/src/api/generated/types.ts"
	@echo "make gen-backend    # 生成后端 DTO/接口 -> backend/src/main/generated/ (勿手改)"
	@echo "make mock           # 基于契约起 mock server (前端可在后端未就绪时联调)"

lint-contract:
	npx --yes @redocly/cli@latest lint $(OPENAPI)

gen-frontend:
	@mkdir -p frontend/src/api/generated
	npx --yes openapi-typescript@7 $(OPENAPI) -o frontend/src/api/generated/types.ts
	@echo "✓ 前端类型已生成: frontend/src/api/generated/types.ts"

gen-backend:
	npx --yes @openapitools/openapi-generator-cli@latest generate \
	  -i $(OPENAPI) -g spring -o backend \
	  --additional-properties=interfaceOnly=true,useSpringBoot3=true,useJakartaEe=true,useTags=true,openApiNullable=false,sourceFolder=src/main/generated,modelPackage=cn.edu.buaa.quju.generated.dto,apiPackage=cn.edu.buaa.quju.generated.api
	@echo "✓ 后端 DTO/接口已生成: backend/src/main/generated/ (勿手改; controller 可 implements 生成的接口)"

mock:
	npx --yes @stoplight/prism-cli@latest mock $(OPENAPI)
