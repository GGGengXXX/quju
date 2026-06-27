# 前端镜像：多阶段（npm 构建 → nginx 提供静态 + 反代 /v1）。随应用脚手架落地后即可用。
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
