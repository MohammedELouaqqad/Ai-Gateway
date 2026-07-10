import { AppLayout } from '../layouts/AppLayout'
import { EmptyState } from '../components/EmptyState'
import { pages } from '../i18n/fr'

export function SettingsPage() {
  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.settings.title }]}>
      <header className="page-header">
        <h1>{pages.settings.title}</h1>
        <p>{pages.settings.subtitle}</p>
      </header>

      <div className="settings-grid">
        <section className="settings-section card">
          <div className="settings-section__header">
            <h2 className="settings-section__title">Auth Keycloak</h2>
            <p className="settings-section__desc">OIDC · OAuth2 · JWT.</p>
          </div>
          <div className="settings-section__body">
            <EmptyState
              status="roadmap"
              message="Configuration Keycloak non branchée. Mode dev : utilisateur id=1."
              action={{ to: '/profile', label: 'Voir le profil' }}
            />
          </div>
        </section>

        <section className="settings-section card">
          <div className="settings-section__header">
            <h2 className="settings-section__title">Users &amp; Rôles</h2>
          </div>
          <div className="settings-section__body">
            <EmptyState
              status="roadmap"
              message="Gestion des utilisateurs via Keycloak (à venir)."
            />
          </div>
        </section>

        <section className="settings-section card">
          <div className="settings-section__header">
            <h2 className="settings-section__title">Clés API</h2>
          </div>
          <div className="settings-section__body">
            <EmptyState
              status="roadmap"
              message="ApiKeyService non branché côté backend."
              action={{ to: '/providers', label: 'Gérer les providers' }}
            />
          </div>
        </section>
      </div>
    </AppLayout>
  )
}
