import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { ApiKeysManager } from '../components/ApiKeysManager'
import { UsersManager } from '../components/UsersManager'
import { Badge } from '../components/Badge'
import { EmptyState } from '../components/EmptyState'
import { getRoles, isAdmin, isTokenExpired, parseAccessToken } from '../auth/roles'
import { isAuthenticated } from '../auth/session'
import { useI18n } from '../i18n'

/** Claims utiles hors typage de roles.ts (ne pas modifier roles.ts). */
type TokenClaims = NonNullable<ReturnType<typeof parseAccessToken>> & {
  iss?: string
}

function roleBadgeVariant(role: string): 'info' | 'warning' | 'secondary' {
  if (role === 'ADMIN') return 'warning'
  if (role === 'DEVELOPER' || role === 'USER') return 'info'
  return 'secondary'
}

export function SettingsPage() {
  const { pages, common, locale } = useI18n()
  const connected = isAuthenticated()
  const claims = parseAccessToken() as TokenClaims | null
  const expired = isTokenExpired()
  const roles = getRoles()
  const username = claims?.preferred_username?.trim() || '—'
  const issuer = claims?.iss?.trim() || '—'
  const localeTag = locale === 'en' ? 'en-US' : 'fr-FR'
  const expiryLabel =
    claims?.exp == null
      ? '—'
      : new Date(claims.exp * 1000).toLocaleString(localeTag, {
          dateStyle: 'short',
          timeStyle: 'medium',
        })

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.settings.title }]}>
      <header className="page-header">
        <h1>{pages.settings.title}</h1>
        <p>{pages.settings.subtitle}</p>
      </header>

      <div className="settings-grid">
        <section className="settings-section card" aria-labelledby="settings-auth-heading">
          <div className="settings-section__header">
            <h2 id="settings-auth-heading" className="settings-section__title">
              {pages.settings.authTitle}
            </h2>
            <p className="settings-section__desc">{pages.settings.authDesc}</p>
          </div>
          <div className="settings-section__body">
            <dl className="profile-details">
              <div className="profile-details__row">
                <dt>{pages.settings.status}</dt>
                <dd>
                  {!connected ? (
                    <Badge variant="secondary" dot>{common.notConnected}</Badge>
                  ) : expired ? (
                    <Badge variant="error" dot>{pages.settings.tokenExpired}</Badge>
                  ) : (
                    <Badge variant="success" dot>{common.connected}</Badge>
                  )}
                </dd>
              </div>
              <div className="profile-details__row">
                <dt>{pages.settings.user}</dt>
                <dd className="mono">{connected ? username : '—'}</dd>
              </div>
              <div className="profile-details__row">
                <dt>{pages.settings.roles}</dt>
                <dd>
                  {!connected || roles.length === 0 ? (
                    <span className="mono" style={{ color: 'var(--text-muted)' }}>—</span>
                  ) : (
                    <span className="settings-auth__roles">
                      {roles.map((role) => (
                        <Badge key={role} variant={roleBadgeVariant(role)}>
                          {role}
                        </Badge>
                      ))}
                    </span>
                  )}
                </dd>
              </div>
              <div className="profile-details__row">
                <dt>{pages.settings.issuer}</dt>
                <dd
                  className="mono settings-auth__issuer"
                  title={issuer !== '—' ? issuer : undefined}
                >
                  {connected ? issuer : '—'}
                </dd>
              </div>
              <div className="profile-details__row">
                <dt>{pages.settings.expiry}</dt>
                <dd>
                  {!connected ? (
                    '—'
                  ) : (
                    <span className="settings-auth__expiry">
                      <span className="mono">{expiryLabel}</span>
                      {expired && <Badge variant="error">{pages.settings.expired}</Badge>}
                      {!expired && claims?.exp != null && (
                        <Badge variant="success">{pages.settings.valid}</Badge>
                      )}
                    </span>
                  )}
                </dd>
              </div>
            </dl>
            <p className="settings-auth__hint">
              <Link to="/profile" className="card__link">
                {pages.settings.viewProfile}
              </Link>
            </p>
          </div>
        </section>

        <section className="settings-section card">
          <div className="settings-section__header">
            <h2 className="settings-section__title">{pages.settings.usersRoles}</h2>
          </div>
          <div className="settings-section__body">
            {isAdmin() ? (
              <UsersManager />
            ) : (
              <EmptyState status="info" message={pages.settings.usersAdminOnly} />
            )}
          </div>
        </section>

        <section className="settings-section card">
          <div className="settings-section__header">
            <h2 className="settings-section__title">{pages.settings.apiKeys}</h2>
          </div>
          <div className="settings-section__body">
            <ApiKeysManager />
          </div>
        </section>
      </div>
    </AppLayout>
  )
}
