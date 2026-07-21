import { useCallback, useEffect, useState } from 'react'
import { ExternalLink, RefreshCw } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { Badge } from '../components/Badge'
import { ScopeBadge } from '../components/ScopeBadge'
import { fetchProviders } from '../api/providers'
import { fetchUsageByUser, type UsageByUserRow } from '../api/usage'
import type { ApiProvider } from '../api/types'
import { useI18n } from '../i18n'
import { formatTokens } from '../utils/formatTokens'
import { providerToUiType } from '../utils/mappers'
import { withAuthHeaders } from '../auth/session'

type Status = 'UP' | 'DOWN' | 'UNKNOWN'

const GRAFANA_BASE = import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3000'
const PROMETHEUS_BASE = import.meta.env.VITE_PROMETHEUS_URL ?? 'http://localhost:9090'
const GRAFANA_DASHBOARD = `${GRAFANA_BASE}/d/ai-gateway-overview`
const PROMETHEUS_TARGETS = `${PROMETHEUS_BASE}/targets`
const REFRESH_MS = 15_000

interface HealthPayload {
  status?: string
  components?: Record<string, { status?: string }>
}

async function fetchActuatorHealth(): Promise<{ gateway: Status; postgres: Status }> {
  try {
    const res = await fetch('/actuator/health', {
      headers: withAuthHeaders({ Accept: 'application/json' }),
    })
    if (!res.ok) return { gateway: 'DOWN', postgres: 'UNKNOWN' }
    const body = (await res.json()) as HealthPayload
    const gateway: Status = body.status === 'UP' ? 'UP' : 'DOWN'
    const db = body.components?.db?.status ?? body.components?.diskSpace?.status
    const postgres: Status = db === 'UP' ? 'UP' : db === 'DOWN' ? 'DOWN' : 'UNKNOWN'
    return { gateway, postgres }
  } catch {
    return { gateway: 'UNKNOWN', postgres: 'UNKNOWN' }
  }
}

async function fetchPrometheus(): Promise<Status> {
  try {
    const res = await fetch('/prom-api/-/healthy')
    return res.ok ? 'UP' : 'DOWN'
  } catch {
    return 'UNKNOWN'
  }
}

export function AdminHealthPage() {
  const { pages, fmt, common, locale } = useI18n()
  const a = pages.adminHealth
  const [gateway, setGateway] = useState<Status>('UNKNOWN')
  const [postgres, setPostgres] = useState<Status>('UNKNOWN')
  const [prometheus, setPrometheus] = useState<Status>('UNKNOWN')
  const [providers, setProviders] = useState<ApiProvider[]>([])
  const [byUser, setByUser] = useState<UsageByUserRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [health, prom, providerData, users] = await Promise.all([
        fetchActuatorHealth(),
        fetchPrometheus(),
        fetchProviders(),
        fetchUsageByUser(),
      ])
      setGateway(health.gateway)
      setPostgres(health.postgres)
      setPrometheus(prom)
      setProviders(providerData)
      setByUser(users)
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setLoading(false)
    }
  }, [common.loadError])

  useEffect(() => {
    void load()
    const id = window.setInterval(() => void load(), REFRESH_MS)
    return () => window.clearInterval(id)
  }, [load])

  function statusBadge(s: Status, upLabel: string, downLabel: string) {
    const label = s === 'UP' ? upLabel : s === 'DOWN' ? downLabel : a.unknown
    const variant = s === 'UP' ? 'success' : s === 'DOWN' ? 'error' : 'secondary'
    return (
      <Badge variant={variant} dot>
        {label}
      </Badge>
    )
  }

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: a.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{a.title}</h1>
          <p>{a.subtitle}</p>
          <ScopeBadge scope="total" locale={locale}>{a.scopeTotal}</ScopeBadge>
        </div>
        <div className="page-header__actions">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void load()} disabled={loading}>
            <RefreshCw size={14} aria-hidden="true" style={{ marginRight: 6 }} />
            {a.refresh}
          </button>
        </div>
      </header>

      <section className="section" aria-label={a.components}>
        <div className="section__header">
          <h2 className="section__title">{a.components}</h2>
        </div>
        <div className="kpi-grid">
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{a.gateway}</span></div>
            <div className="kpi-card__value" style={{ fontSize: '1.2rem' }}>
              {statusBadge(gateway, a.up, a.down)}
            </div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{a.postgres}</span></div>
            <div className="kpi-card__value" style={{ fontSize: '1.2rem' }}>
              {statusBadge(postgres, a.connected, a.unreachable)}
            </div>
            <span className="kpi-card__hint">/actuator/health</span>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{a.prometheus}</span></div>
            <div className="kpi-card__value" style={{ fontSize: '1.2rem' }}>
              {statusBadge(prometheus, a.connected, a.unreachable)}
            </div>
          </article>
        </div>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{pages.providers.title}</h2>
        </div>
        <div className="card__body" style={{ padding: 0 }}>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{common.name}</th>
                  <th scope="col">{common.type}</th>
                  <th scope="col">{common.status}</th>
                </tr>
              </thead>
              <tbody>
                {providers.map((p) => {
                  const type = providerToUiType(p)
                  return (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td>
                        <span className={`tag ${type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
                          {type === 'onprem' ? 'LOCAL' : 'CLOUD'}
                        </span>
                      </td>
                      <td>
                        <Badge variant={p.enabled ? 'success' : 'secondary'} dot>
                          {p.enabled ? common.active : common.inactive}
                        </Badge>
                      </td>
                    </tr>
                  )
                })}
                {!loading && providers.length === 0 && (
                  <tr>
                    <td colSpan={3} className="table-empty">{common.noProviders}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{a.byUser}</h2>
        </div>
        <div className="card__body" style={{ padding: 0 }}>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{a.userCol}</th>
                  <th scope="col">{a.req}</th>
                  <th scope="col">Tokens</th>
                  <th scope="col">Cost</th>
                </tr>
              </thead>
              <tbody>
                {byUser.map((u) => (
                  <tr key={u.user}>
                    <td>{u.user}</td>
                    <td className="mono">{fmt.num(u.requests)}</td>
                    <td className="mono">{formatTokens(u.tokens)}</td>
                    <td className="mono">{fmt.currency(u.cost)}</td>
                  </tr>
                ))}
                {!loading && byUser.length === 0 && (
                  <tr>
                    <td colSpan={4} className="table-empty">—</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section className="section" aria-label={a.tools}>
        <div className="section__header">
          <h2 className="section__title">{a.tools}</h2>
        </div>
        <div className="obs-tool-card__actions">
          <a className="btn btn--primary btn--sm" href={GRAFANA_DASHBOARD} target="_blank" rel="noopener noreferrer">
            {a.openGrafana}
            <ExternalLink size={14} aria-hidden="true" style={{ marginLeft: 6 }} />
          </a>
          <a className="btn btn--ghost btn--sm" href={PROMETHEUS_TARGETS} target="_blank" rel="noopener noreferrer">
            {a.openPrometheus}
            <ExternalLink size={14} aria-hidden="true" style={{ marginLeft: 6 }} />
          </a>
        </div>
      </section>
    </AppLayout>
  )
}
