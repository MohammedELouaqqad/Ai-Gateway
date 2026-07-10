import { Link } from 'react-router-dom'
import { getAppEnv, envBadgeClass } from '../utils/env'
import { pages } from '../i18n/fr'

export function LoginPage() {
  const env = getAppEnv()

  return (
    <div className="login-page">
      <div className="login-page__panel">
        <div className="login-card">
          <div className="login-card__brand">
            <div className="sidebar__logo" aria-hidden="true">AI</div>
            <div>
              <h1 className="login-card__title">AI Gateway</h1>
              <p className="login-card__subtitle">{pages.login.subtitle}</p>
            </div>
            <span className={envBadgeClass(env)} aria-label={`Environnement ${env}`}>{env}</span>
          </div>

          <div className="login-card__sso-block">
            <p className="login-card__desc">
              Authentification Keycloak — non configurée pour l&apos;instant.
            </p>
            <Link to="/" className="btn btn--primary login-card__submit">
              Continuer sans connexion (dev)
            </Link>
          </div>
        </div>

        <p className="login-page__back">
          <Link to="/">{pages.chat.title}</Link>
        </p>
      </div>
    </div>
  )
}
