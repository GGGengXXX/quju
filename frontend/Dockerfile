# 前端镜像：多阶段（npm 构建 → nginx 提供静态 + 反代 /v1）。随应用脚手架落地后即可用。
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# 高德地图 Key（Vite 构建期注入；值来自 deploy/.env 的 VITE_AMAP_KEY → compose build.args）
ARG VITE_AMAP_KEY=
ENV VITE_AMAP_KEY=$VITE_AMAP_KEY
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
