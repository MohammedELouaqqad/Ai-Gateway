import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { pages } from '../i18n/fr'

export function RoutingPage() {
  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.routing.title }]}>
      <header className="page-header">
        <h1>{pages.routing.title}</h1>
        <p>{pages.routing.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__body">
          <EmptyState
            status="roadmap"
            title="Routage avancé"
            message="Non disponible pour l'instant. Le chat utilise le modèle sélectionné et son provider associé."
            action={{ to: '/models', label: 'Voir les modèles' }}
          />
        </div>
      </section>
    </AppLayout>
  )
}
