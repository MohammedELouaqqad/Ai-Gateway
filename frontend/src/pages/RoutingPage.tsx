import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { useI18n } from '../i18n'

export function RoutingPage() {
  const { pages } = useI18n()
  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.routing.title }]}>
      <header className="page-header">
        <h1>{pages.routing.title}</h1>
        <p>{pages.routing.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__body">
          <EmptyState status="roadmap" title={pages.routing.title} message={pages.routing.subtitle} />
        </div>
      </section>
    </AppLayout>
  )
}
