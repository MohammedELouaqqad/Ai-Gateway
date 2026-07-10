import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { pages } from '../i18n/fr'

export function RateLimitingPage() {
  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.rateLimit.title }]}>
      <header className="page-header">
        <h1>{pages.rateLimit.title}</h1>
        <p>{pages.rateLimit.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__body">
          <EmptyState
            status="roadmap"
            title="Rate limiting"
            message="Non disponible pour l'instant. Nécessite Redis et un service de quotas côté backend."
            action={{ to: '/settings', label: 'Voir les paramètres' }}
          />
        </div>
      </section>
    </AppLayout>
  )
}
