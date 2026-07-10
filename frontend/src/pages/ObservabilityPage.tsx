import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { pages } from '../i18n/fr'

export function ObservabilityPage() {
  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.observability.title }]}>
      <header className="page-header">
        <h1>{pages.observability.title}</h1>
        <p>{pages.observability.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__body">
          <EmptyState
            status="roadmap"
            title="Observabilité"
            message="Métriques et alertes non branchées. Les tokens, coûts et latences sont déjà visibles dans Consommation."
            action={{ to: '/usage', label: 'Ouvrir Consommation' }}
          />
        </div>
      </section>
    </AppLayout>
  )
}
