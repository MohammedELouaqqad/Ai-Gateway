import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { ScopeBadge } from '../components/ScopeBadge'
import { fetchProviders } from '../api/providers'
import { fetchUsageLogs, fetchUsageSummary, type UsageSummaryExtended } from '../api/usage'
import type { ApiProvider, ApiUsageLog } from '../api/types'
import { useI18n } from '../i18n'
import { formatTokens } from '../utils/formatTokens'
import { providerToUiType } from '../utils/mappers'
import { formatMs, formatPercent, successErrorRate } from '../utils/usageStats'
import { scopeBadgeLabel, scopeToRange, type TimeScope } from '../utils/timeScope'

const REFRESH_MS = 15_000

export function DashboardPage() {
  const { pages, fmt, labels, common, locale } = useI18n()
  const dash = pages.dashboard
  const localeTag = locale === 'en' ? 'en-US' : 'fr-FR'
  const [scope, setScope] = useState<TimeScope>('24h')
  const [providers, setProviders] = useState<ApiProvider[]>([])
  const [usage, setUsage] = useState<UsageSummaryExtended | null>(null)
  const [logs, setLogs] = useState<ApiUsageLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      const range = scopeToRange(scope)
      try {
        const [providerData, usageData, logsData] = await Promise.all([
          fetchProviders(),
          fetchUsageSummary(range),
          fetchUsageLogs(),
        ])
        if (cancelled) return
        setProviders(providerData)
        setUsage(usageData)
        // Filter logs client-side for error rate when session = all; for ranges use summary.errorRate
        if (scope === 'session') {
          setLogs(logsData)
        } else {
          const fromMs = range.from ? new Date(range.from).getTime() : 0
          const toMs = range.to ? new Date(range.to).getTime() : Date.now()
          setLogs(
            logsData.filter((l) => {
              const t = new Date(l.createdAt ?? 0).getTime()
              return t >= fromMs && t <= toMs
            }),
          )
        }
        setError(null)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : common.loadError)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    const id = window.setInterval(() => void load(), REFRESH_MS)
    return () => {
      cancelled = true
      window.clearInterval(id)
    }
  }, [scope, common.loadError])

  const activeCount = providers.filter((p) => p.enabled).length
  const inactiveCount = providers.length - activeCount
  const localCount = providers.filter((p) => providerToUiType(p) === 'onprem').length
  const cloudCount = providers.length - localCount
  const reliability = useMemo(() => successErrorRate(logs), [logs])
  const errorRate = usage?.errorRate ?? reliability.errorRate
  const avgLatency = usage?.avgLatencyMs ?? null
  const scopeLabel = scopeBadgeLabel(scope, locale)

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: dash.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{dash.title}</h1>
          <p>{dash.subtitle}</p>
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
        </div>
        <div className="page-header__actions">
          <Link to="/" className="btn btn--primary btn--sm">{dash.openChat}</Link>
        </div>
      </header>

      <section className="dashboard-kpi-section" aria-label={dash.kpiAria}>
        <div className="section__header">
          <h2 className="section__title">{dash.kpiSection}</h2>
        </div>
        <div className="kpi-grid">
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{labels.requests}</span>
              <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            </div>
            <div className="kpi-card__value">{usage ? fmt.num(usage.requestCount) : '—'}</div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{dash.errorRate}</span>
              <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            </div>
            <div className="kpi-card__value">{formatPercent(errorRate, localeTag)}</div>
            <span className="kpi-card__hint">
              {usage?.errorCount ?? reliability.error}/{usage?.requestCount ?? reliability.total} ERR
            </span>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{dash.avgLatency}</span>
              <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            </div>
            <div className="kpi-card__value">{formatMs(avgLatency, localeTag)}</div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{labels.providers}</span>
              <ScopeBadge scope="total" locale={locale} />
            </div>
            <div className="kpi-card__value">{providers.length}</div>
            <span className="kpi-card__hint">
              {dash.providersStatus
                .replace('{active}', String(activeCount))
                .replace('{inactive}', String(inactiveCount))}
            </span>
            <span className="kpi-card__hint">
              {dash.providersType
                .replace('{local}', String(localCount))
                .replace('{cloud}', String(cloudCount))}
            </span>
          </article>
        </div>
      </section>

      <section className="section card usage-summary-strip">
        <div className="card__header">
          <h2 className="card__title">{dash.usageStrip}</h2>
          <Link to="/usage" className="card__link">{dash.viewUsageDetail}</Link>
        </div>
        <div className="card__body">
          <div className="usage-summary-strip__grid">
            <div>
              <span className="kpi-card__label">{labels.tokens}</span>
              <div className="usage-summary-strip__value">
                {usage ? formatTokens(usage.totalTokens) : '—'}
              </div>
              <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            </div>
            <div>
              <span className="kpi-card__label">{labels.estimatedCost}</span>
              <div className="usage-summary-strip__value">
                {usage ? fmt.currency(usage.totalCost) : '—'}
              </div>
              <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            </div>
            <div>
              <span className="kpi-card__label">{labels.requests}</span>
              <div className="usage-summary-strip__value">
                {usage ? fmt.num(usage.requestCount) : '—'}
              </div>
              <ScopeBadge scope="session" locale={locale}>{scopeLabel}</ScopeBadge>
            </div>
          </div>
        </div>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{dash.providersTitle}</h2>
          <Link to="/providers" className="card__link">{common.manage}</Link>
        </div>
        <div className="card__body">
          {!loading && providers.length === 0 ? (
            <p className="table-empty">{common.noProviders}</p>
          ) : (
            <div className="provider-health-grid">
              {providers.map((p) => {
                const type = providerToUiType(p)
                return (
                  <div
                    key={p.id}
                    className={`provider-health-item${type === 'onprem' ? ' provider-health-item--onprem' : ''}`}
                  >
                    <div className="provider-health-item__tags">
                      <span className="provider-health-item__name">{p.name}</span>
                      <div className="provider-health-item__meta">
                        <span className={`tag ${type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
                          {type === 'onprem' ? 'LOCAL' : 'CLOUD'}
                        </span>
                        <Badge variant={p.enabled ? 'success' : 'secondary'} dot>
                          {p.enabled ? common.active : common.inactive}
                        </Badge>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </section>
    </AppLayout>
  )
}
