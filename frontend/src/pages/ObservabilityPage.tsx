import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Activity, ExternalLink, RefreshCw } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { Badge } from '../components/Badge'
import { ScopeBadge } from '../components/ScopeBadge'
import { fetchUsageLogs, fetchUsageSummary } from '../api/usage'
import type { ApiUsageLog } from '../api/types'
import { useI18n } from '../i18n'
import {
  formatMs,
  formatPercent,
  latencyByKind,
  latencyPercentiles,
  successErrorRate,
} from '../utils/usageStats'
import { scopeBadgeLabel, scopeToRange, type TimeScope } from '../utils/timeScope'

type HealthStatus = 'UP' | 'DOWN' | 'UNKNOWN'

const GRAFANA_BASE = import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3000'
const PROMETHEUS_BASE = import.meta.env.VITE_PROMETHEUS_URL ?? 'http://localhost:9090'
const GRAFANA_DASHBOARD = `${GRAFANA_BASE}/d/ai-gateway-overview`
const PROMETHEUS_TARGETS = `${PROMETHEUS_BASE}/targets`
const REFRESH_MS = 15_000

async function fetchGatewayHealth(): Promise<HealthStatus> {
  try {
    const res = await fetch('/actuator/health', { headers: { Accept: 'application/json' } })
    if (!res.ok) return 'DOWN'
    const body = (await res.json()) as { status?: string }
    return body.status === 'UP' ? 'UP' : 'DOWN'
  } catch {
    return 'UNKNOWN'
  }
}

export function ObservabilityPage() {
  const { pages, common, locale } = useI18n()
  const obs = pages.observability
  const localeTag = locale === 'en' ? 'en-US' : 'fr-FR'
  const [scope, setScope] = useState<TimeScope>('24h')
  const [health, setHealth] = useState<HealthStatus>('UNKNOWN')
  const [logs, setLogs] = useState<ApiUsageLog[]>([])
  const [errorRate, setErrorRate] = useState<number | null>(null)
  const [errorCount, setErrorCount] = useState(0)
  const [requestCount, setRequestCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshedAt, setRefreshedAt] = useState<Date | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    const range = scopeToRange(scope)
    try {
      const [healthStatus, summary, allLogs] = await Promise.all([
        fetchGatewayHealth(),
        fetchUsageSummary(range),
        fetchUsageLogs(),
      ])
      setHealth(healthStatus)
      setErrorRate(summary.errorRate ?? null)
      setErrorCount(summary.errorCount ?? 0)
      setRequestCount(summary.requestCount)
      if (scope === 'session') {
        setLogs(allLogs)
      } else {
        const fromMs = range.from ? new Date(range.from).getTime() : 0
        const toMs = range.to ? new Date(range.to).getTime() : Date.now()
        setLogs(
          allLogs.filter((l) => {
            const t = new Date(l.createdAt ?? 0).getTime()
            return t >= fromMs && t <= toMs
          }),
        )
      }
      setRefreshedAt(new Date())
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setLoading(false)
    }
  }, [scope, common.loadError])

  useEffect(() => {
    void load()
    const id = window.setInterval(() => void load(), REFRESH_MS)
    return () => window.clearInterval(id)
  }, [load])

  const percentiles = useMemo(() => latencyPercentiles(logs), [logs])
  const byKind = useMemo(() => latencyByKind(logs), [logs])
  const reliability = useMemo(() => successErrorRate(logs), [logs])
  const rate = errorRate ?? reliability.errorRate
  const scopeLabel = scopeBadgeLabel(scope, locale)

  const healthLabel =
    health === 'UP' ? obs.healthUp : health === 'DOWN' ? obs.healthDown : obs.healthUnknown
  const healthVariant = health === 'UP' ? 'success' : health === 'DOWN' ? 'error' : 'secondary'

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: obs.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{obs.title}</h1>
          <p>{obs.subtitle}</p>
          <div className="scope-filter-bar">
            <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            {(['24h', '7d', '30d', 'session'] as TimeScope[]).map((s) => (
              <button
                key={s}
                type="button"
                className={`btn btn--sm ${scope === s ? 'btn--primary' : 'btn--ghost'}`}
                onClick={() => setScope(s)}
              >
                {scopeBadgeLabel(s, locale)}
              </button>
            ))}
          </div>
          {refreshedAt && (
            <p className="section__subtitle" style={{ marginTop: '0.35rem' }}>
              {obs.lastRefresh.replace(
                '{time}',
                refreshedAt.toLocaleTimeString(localeTag, { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
              )}
            </p>
          )}
        </div>
        <div className="page-header__actions">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void load()} disabled={loading}>
            <RefreshCw size={14} aria-hidden="true" style={{ marginRight: 6 }} />
            {obs.refresh}
          </button>
          <Link to="/usage" className="btn btn--primary btn--sm">{obs.openUsage}</Link>
        </div>
      </header>

      <section className="section" aria-label={obs.statusSection}>
        <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(2, 1fr)' }}>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{obs.gatewayHealth}</span>
              <Activity size={16} aria-hidden="true" />
            </div>
            <div className="kpi-card__value" style={{ fontSize: '1.25rem' }}>
              <Badge variant={healthVariant} dot>{healthLabel}</Badge>
            </div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{obs.usageLinkHint}</span>
            </div>
            <Link to="/usage" className="btn btn--ghost btn--sm" style={{ marginTop: '0.5rem' }}>
              {obs.openUsage}
            </Link>
          </article>
        </div>
      </section>

      <section className="section" aria-label={obs.reliabilitySection}>
        <div className="section__header">
          <h2 className="section__title">{obs.reliabilitySection}</h2>
          <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
        </div>
        <div className="kpi-grid" style={{ gridTemplateColumns: '1fr' }}>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{obs.errorRate}</span>
            </div>
            <div className="kpi-card__value">{formatPercent(rate, localeTag)}</div>
            <span className="kpi-card__hint">
              {errorCount || reliability.error}/{requestCount || reliability.total} ERR
            </span>
          </article>
        </div>
      </section>

      <section className="section" aria-label={obs.latencySection}>
        <div className="section__header">
          <h2 className="section__title">{obs.latencySection}</h2>
          <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
        </div>
        <div className="kpi-grid">
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{obs.latencyP50}</span></div>
            <div className="kpi-card__value">{formatMs(percentiles.p50, localeTag)}</div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{obs.latencyP95}</span></div>
            <div className="kpi-card__value">{formatMs(percentiles.p95, localeTag)}</div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{obs.latencyLocal}</span></div>
            <div className="kpi-card__value" style={{ fontSize: '1.05rem' }}>
              {formatMs(byKind.local.p50, localeTag)} / {formatMs(byKind.local.p95, localeTag)}
            </div>
            <span className="kpi-card__hint">n={byKind.local.count}</span>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header"><span className="kpi-card__label">{obs.latencyCloud}</span></div>
            <div className="kpi-card__value" style={{ fontSize: '1.05rem' }}>
              {formatMs(byKind.cloud.p50, localeTag)} / {formatMs(byKind.cloud.p95, localeTag)}
            </div>
            <span className="kpi-card__hint">n={byKind.cloud.count}</span>
          </article>
        </div>
      </section>

      <section className="section" aria-label={obs.toolsSection}>
        <div className="section__header">
          <h2 className="section__title">{obs.toolsSection}</h2>
        </div>
        <div className="obs-tools-grid" style={{ gridTemplateColumns: 'repeat(2, 1fr)' }}>
          <article className="card obs-tool-card">
            <div className="card__body">
              <p className="obs-tool-card__desc">{obs.grafanaDesc}</p>
              <a className="btn btn--primary btn--sm" href={GRAFANA_DASHBOARD} target="_blank" rel="noopener noreferrer">
                {obs.openGrafanaDash}
                <ExternalLink size={14} aria-hidden="true" style={{ marginLeft: 6 }} />
              </a>
            </div>
          </article>
          <article className="card obs-tool-card">
            <div className="card__body">
              <p className="obs-tool-card__desc">{obs.prometheusDesc}</p>
              <a className="btn btn--primary btn--sm" href={PROMETHEUS_TARGETS} target="_blank" rel="noopener noreferrer">
                {obs.openPrometheusTargets}
                <ExternalLink size={14} aria-hidden="true" style={{ marginLeft: 6 }} />
              </a>
            </div>
          </article>
        </div>
      </section>

      <section className="section card">
        <div className="card__body">
          <h2 className="card__title" style={{ marginBottom: '0.5rem' }}>{obs.tipTitle}</h2>
          <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.9rem' }}>{obs.tipBody}</p>
        </div>
      </section>
    </AppLayout>
  )
}
