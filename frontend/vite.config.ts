import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const apiProxyTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        // SSE / long streams must not be buffered by the Vite proxy
        timeout: 0,
        proxyTimeout: 0,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, _req, res) => {
            const contentType = proxyRes.headers['content-type']
            const isEventStream =
              typeof contentType === 'string' && contentType.includes('text/event-stream')
            if (!isEventStream) return

            // Prevent intermediary/proxy buffering of streamed chunks
            res.setHeader('Cache-Control', 'no-cache, no-transform')
            res.setHeader('X-Accel-Buffering', 'no')
            res.setHeader('Connection', 'keep-alive')
            delete proxyRes.headers['content-length']
            delete proxyRes.headers['content-encoding']
          })
        },
      },
    },
  },
})
