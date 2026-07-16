import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { fetchProviders } from '../api/providers'
import { fetchUsageSummary } from '../api/usage'
import type { ApiProvider, UsageSummary } from '../api/types'
import { useI18n } from '../i18n'
import { providerToUiType } from '../utils/mappers'

export function DashboardPage() {
  const { pages, fmt, labels, common } = useI18n()
  const [providers, setProviders] = useState<ApiProvider[]>([])
  const [usage, setUsage] = useState<UsageSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([fetchProviders(), fetchUsageSummary()])
      .then(([providerData, usageData]) => {
        if (!cancelled) {
          setProviders(providerData)
          setUsage(usageData)
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : common.loadError)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [common.loadError])

  const activeCount = providers.filter((p) => p.enabled).length
  const localCount = providers.filter((p) => providerToUiType(p) === 'onprem').length

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.dashboard.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{pages.dashboard.title}</h1>
          <p>{pages.dashboard.subtitle}</p>
        </div>
        <div className="page-header__actions">
          <Link to="/" className="btn btn--primary btn--sm">{pages.dashboard.openChat}</Link>
        </div>
      </header>

      <section className="dashboard-kpi-section" aria-label={pages.dashboard.kpiAria}>
        <div className="section__header">
          <h2 className="section__title">{pages.dashboard.kpiSection}</h2>
          <Link to="/usage" className="card__link">{pages.dashboard.viewUsageDetail}</Link>
        </div>
        <div className="kpi-grid">
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{labels.providers}</span>
            </div>
            <div className="kpi-card__value">{providers.length}</div>
            <span className="kpi-card__trend kpi-card__trend--neutral">
              {pages.dashboard.providersTrend
                .replace('{active}', String(activeCount))
                .replace('{local}', String(localCount))}
            </span>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{labels.tokens}</span>
            </div>
            <div className="kpi-card__value">{usage ? fmt.num(usage.totalTokens) : '—'}</div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{labels.estimatedCost}</span>
            </div>
            <div className="kpi-card__value">{usage ? fmt.currency(usage.totalCost) : '—'}</div>
          </article>
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{labels.requests}</span>
            </div>
            <div className="kpi-card__value">{usage ? fmt.num(usage.requestCount) : '—'}</div>
          </article>
        </div>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{pages.dashboard.providersTitle}</h2>
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
                    <span
                      className={`provider-health-item__dot ${p.enabled ? 'provider-health-item__dot--ok' : 'provider-health-item__dot--error'}`}
                      aria-label={p.enabled ? common.active : common.inactive}
                    />
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
