import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { beginLogin } from './keycloak'
import { clearAccessToken, clearIdToken, isAuthenticated } from './session'
import { hasRole, isTokenExpired, type AppRole } from './roles'
import { LanguageToggle } from '../components/LanguageToggle'
import { ThemeToggle } from '../components/ThemeToggle'
import { useI18n } from '../i18n'

/** Redirige vers Keycloak (page de login native) si non authentifié. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const needsLogin = !isAuthenticated() || isTokenExpired()

  useEffect(() => {
    if (!needsLogin) return
    if (isTokenExpired()) {
      clearAccessToken()
      clearIdToken()
    }
    const redirect = location.pathname + location.search
    void beginLogin(redirect || '/')
  }, [needsLogin, location.pathname, location.search])

  if (needsLogin) {
    return (
      <div className="login-page">
        <div className="login-page__panel">
          <div className="login-card">
            <p className="login-card__desc">Keycloak…</p>
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
