import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { beginLogin, beginRegister } from './keycloak'
import { clearAccessToken, clearIdToken, isAuthenticated } from './session'
import { hasRole, isTokenExpired, type AppRole } from './roles'
import { LanguageToggle } from '../components/LanguageToggle'
import { ThemeToggle } from '../components/ThemeToggle'
import { useI18n } from '../i18n'

/**
 * Porte d'entrée non-authentifiée : écran interactif (pas de redirect auto) avec
 * "Se connecter" et "Créer un compte" — les deux mènent à Keycloak, jamais de formulaire
 * local. "Créer un compte" reste toujours visible : si `registrationAllowed` est à `false`
 * sur le realm, Keycloak retombe de lui-même sur l'écran de login (vérifié empiriquement,
 * rien à répliquer côté app).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { pages } = useI18n()
  const needsLogin = !isAuthenticated() || isTokenExpired()

  useEffect(() => {
    if (needsLogin && isTokenExpired()) {
      clearAccessToken()
      clearIdToken()
    }
  }, [needsLogin])

  if (needsLogin) {
    const redirect = (location.pathname + location.search) || '/'
    return (
      <div className="login-page">
        <div className="login-page__panel">
          <div className="login-page__prefs">
            <LanguageToggle />
            <ThemeToggle />
          </div>
          <div className="login-card">
            <h1 className="login-card__title">{pages.login.title}</h1>
            <p className="login-card__desc">{pages.login.desc}</p>
            <button
              type="button"
              className="btn btn--primary login-card__submit"
              onClick={() => void beginLogin(redirect)}
            >
              {pages.login.loginButton}
            </button>
            <button
              type="button"
              className="btn btn--ghost login-card__submit"
              onClick={() => void beginRegister(redirect)}
            >
              {pages.login.registerButton}
            </button>
          </div>
        </div>
      </div>
    )
  }

  return children
}

export function RequireRole({
  role,
  children,
  fallback = '/',
}: {
  role: AppRole
  children: ReactNode
  fallback?: string
}) {
  if (!hasRole(role)) {
    return <Navigate to={fallback} replace />
  }
  return children
}

export function AccessDeniedPage() {
  const { pages } = useI18n()
  return (
    <div className="login-page">
      <div className="login-page__panel">
        <div className="login-page__prefs">
          <LanguageToggle />
          <ThemeToggle />
        </div>
        <div className="login-card">
          <h1 className="login-card__title">{pages.accessDenied.title}</h1>
          <p className="login-card__desc">{pages.accessDenied.desc}</p>
          <a className="btn btn--primary login-card__submit" href="/">
            {pages.accessDenied.back}
          </a>
        </div>
      </div>
    </div>
  )
}
