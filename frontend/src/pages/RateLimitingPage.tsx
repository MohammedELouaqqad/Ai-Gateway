import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { useI18n } from '../i18n'

export function RateLimitingPage() {
  const { pages } = useI18n()
  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.rateLimit.title }]}>
      <header className="page-header">
        <h1>{pages.rateLimit.title}</h1>
        <p>{pages.rateLimit.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__body">
          <EmptyState status="roadmap" title={pages.rateLimit.title} message={pages.rateLimit.subtitle} />
        </div>
      </section>
    </AppLayout>
  )
}
