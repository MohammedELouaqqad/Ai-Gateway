import { useEffect, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { completeLoginFromCallback } from '../auth/keycloak'
import { LanguageToggle } from '../components/LanguageToggle'
import { ThemeToggle } from '../components/ThemeToggle'
import { useI18n } from '../i18n'

/**
 * Callback OAuth Keycloak (Authorization Code + PKCE).
 * Pas de formulaire — échange le code puis redirige vers l'app.
 */
export function AuthCallbackPage() {
  const { pages } = useI18n()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // completeLoginFromCallback déduplique les appels (Strict Mode rejoue cet effet en dev)
    completeLoginFromCallback(location.search)
      .then((redirect) => {
        if (!cancelled) navigate(redirect, { replace: true })
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : pages.authCallback.failed)
        }
      })
    return () => {
      cancelled = true
    }
  }, [location.search, navigate, pages.authCallback.failed])

  return (
    <div className="login-page">
      <div className="login-page__panel">
        <div className="login-page__prefs">
          <LanguageToggle />
          <ThemeToggle />
        </div>
        <div className="login-card">
          <h1 className="login-card__title">{pages.authCallback.title}</h1>
          {error ? (
            <>
              <p className="login-card__error" role="alert">{error}</p>
              <button
                type="button"
                className="btn btn--primary login-card__submit"
                onClick={() => {
                  void import('../auth/keycloak').then((m) => m.beginLogin('/'))
                }}
              >
                {pages.authCallback.retry}
              </button>
            </>
          ) : (
            <p className="login-card__desc">{pages.authCallback.waiting}</p>
          )}
        </div>
      </div>
    </div>
  )
}
