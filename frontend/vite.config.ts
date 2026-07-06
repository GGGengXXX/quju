import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import basicSsl from '@vitejs/plugin-basic-ssl'

// 定位(Geolocation)只在安全上下文可用：localhost 或 HTTPS。
// 用手机/局域网 IP 访问时必须走 HTTPS，设 VITE_HTTPS=1 开启自签证书。
const useHttps = !!process.env.VITE_HTTPS

export default defineConfig({
  plugins: [vue(), ...(useHttps ? [basicSsl()] : [])],
  server: {
    host: true, // 监听 0.0.0.0，手机/局域网可访问
    port: Number(process.env.VITE_PORT) || 5173,
    https: useHttps ? {} : undefined, // basicSsl 注入自签证书，此处开启 HTTPS 服务
    // 本地开发时把 /v1 代理到后端（部署时由 nginx 反代）
    proxy: {
      '/v1': {
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
