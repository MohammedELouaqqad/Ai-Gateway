import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const apiProxyTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'
const keycloakProxyTarget = process.env.KEYCLOAK_PROXY_TARGET ?? 'http://localhost:8180'
// Actuator lives on its own management.server.port (8081), separate from the API port (8080)
// and never published to the host in docker-compose.yml — reachable here only because this
// dev-server container shares the Docker network with the backend container.
const managementProxyTarget = process.env.MANAGEMENT_PROXY_TARGET ?? 'http://localhost:8081'
const inDocker = process.env.DOCKER === 'true' || process.env.CHOKIDAR_USEPOLLING === 'true'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    watch: inDocker
      ? {
          usePolling: true,
          interval: 500,
        }
      : undefined,
    hmr: inDocker
      ? {
          // Navigateur sur l'hôte → HMR via port publié
          clientPort: Number(process.env.HMR_CLIENT_PORT ?? 5173),
        }
      : undefined,
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
      // Évite CORS navigateur → Keycloak (échange code→token PKCE en dev).
      // changeOrigin ferait Host=host.docker.internal en Docker → iss JWT invalide côté backend.
      // On force Host public (localhost:8180) pour que iss = KEYCLOAK_ISSUER_URI.
      '/realms': {
        target: keycloakProxyTarget,
        changeOrigin: true,
        configure: (proxy) => {
          const publicHost = process.env.KEYCLOAK_PUBLIC_HOST ?? 'localhost:8180'
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Host', publicHost)
          })
        },
      },
      // Actuator (health) — hub Observability. Separate target: management.server.port (8081).
      '/actuator': {
        target: managementProxyTarget,
        changeOrigin: true,
      },
      // Prometheus health (scrape status) — avoid browser CORS
      '/prom-api': {
        target: process.env.PROMETHEUS_PROXY_TARGET ?? 'http://localhost:9090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/prom-api/, ''),
      },
    },
  },
})
