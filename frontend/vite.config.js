import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Proxy για τα κανονικά REST αιτήματα
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // Proxy για τα WebSockets (SockJS)
      '/ws': {
        target: 'http://localhost:8080',
        ws: true, // ΕΝΕΡΓΟΠΟΙΗΣΗ WEBSOCKET PROXY
        changeOrigin: true,
      }
    }
  }
})