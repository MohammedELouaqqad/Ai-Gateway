import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { fetchProviders } from '../api/providers'
import { fetchUsageSummary } from '../api/usage'
import type { ApiProvider, UsageSummary } from '../api/types'
import { fmt, pages } from '../i18n/fr'
import { providerToUiType } from '../utils/mappers'

export function DashboardPage() {
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
          setError(e instanceof Error ? e.message : 'Erreur chargement')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

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
          <Link to="/usage" className="btn btn--ghost btn--sm">Consommation</Link>
          <Link to="/" className="btn btn--primary btn--sm">Ouvrir le chat</Link>
        </div>
      </header>

      <section className="kpi-grid" aria-label="Indicateurs clés">
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">PROVIDERS</span>
          </div>
          <div className="kpi-card__value">{providers.length}</div>
          <span className="kpi-card__trend kpi-card__trend--neutral">
            {activeCount} actif{activeCount !== 1 ? 's' : ''} · {localCount} local
          </span>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">TOKENS</span>
          </div>
          <div className="kpi-card__value">{usage ? fmt.num(usage.totalTokens) : '—'}</div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">COÛT ESTIMÉ</span>
          </div>
          <div className="kpi-card__value">{usage ? fmt.currency(usage.totalCost) : '—'}</div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">REQUÊTES</span>
          </div>
          <div className="kpi-card__value">{usage ? fmt.num(usage.requestCount) : '—'}</div>
        </article>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">Providers</h2>
          <Link to="/providers" className="card__link">Gérer →</Link>
        </div>
        <div className="card__body">
          {!loading && providers.length === 0 ? (
            <p className="table-empty">Aucun provider configuré</p>
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
                      aria-label={p.enabled ? 'Actif' : 'Inactif'}
                    />
                    <div className="provider-health-item__tags">
                      <span className="provider-health-item__name">{p.name}</span>
                      <div className="provider-health-item__meta">
                        <span className={`tag ${type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
                          {type === 'onprem' ? 'LOCAL' : 'CLOUD'}
                        </span>
                        <Badge variant={p.enabled ? 'success' : 'secondary'} dot>
                          {p.enabled ? 'Actif' : 'Inactif'}
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
