import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api/v1/productos': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/api/v1/pedidos': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/usuarios-api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/usuarios-api/, ''),
      },
    },
  },
})
