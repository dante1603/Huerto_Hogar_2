import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const cleanProxy = (target, rewrite) => ({
  target,
  changeOrigin: true,
  ...(rewrite ? { rewrite } : {}),
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => {
      proxyReq.removeHeader('origin')
    })
  },
})

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api/v1/productos': cleanProxy('http://127.0.0.1:8080'),
      '/api/v1/pedidos': cleanProxy('http://127.0.0.1:8082'),
      '/api/v1/boletas': cleanProxy('http://127.0.0.1:8083'),
      '/api/v1/detalles-boleta': cleanProxy('http://127.0.0.1:8084'),
      '/usuarios-api': cleanProxy('http://127.0.0.1:8081', (path) => path.replace(/^\/usuarios-api/, '')),
    },
  },
})
