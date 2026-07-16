import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { useI18n } from '../i18n'

export function ObservabilityPage() {
  const { pages } = useI18n()
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
            title={pages.observability.title}
            message={pages.observability.subtitle}
            action={{ to: '/usage', label: pages.usage.title }}
          />
        </div>
      </section>
    </AppLayout>
  )
}
