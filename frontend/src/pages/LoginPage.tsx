import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { loginWithPassword } from '../auth/keycloak'
import { clearAccessToken, isAuthenticated } from '../auth/session'
import { LanguageToggle } from '../components/LanguageToggle'
import { ThemeToggle } from '../components/ThemeToggle'
import { useI18n } from '../i18n'

export function LoginPage() {
  const { pages, common } = useI18n()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const redirectTo = searchParams.get('redirect') || '/'

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await loginWithPassword(username.trim(), password)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : pages.login.failed)
    } finally {
      setLoading(false)
    }
  }

  function onLogout() {
    clearAccessToken()
    setError(null)
  }

  return (
    <div className="login-page">
      <div className="login-page__panel">
        <div className="login-page__prefs">
          <LanguageToggle />
          <ThemeToggle />
        </div>
        <div className="login-card">
          <div className="login-card__brand">
            <div className="sidebar__logo" aria-hidden="true">AI</div>
            <div>
              <h1 className="login-card__title">AI Gateway</h1>
              <p className="login-card__subtitle">{pages.login.subtitle}</p>
            </div>
          </div>

          {isAuthenticated() ? (
            <div className="login-card__sso-block">
              <p className="login-card__desc">{pages.login.sessionActive}</p>
              <Link to="/" className="btn btn--primary login-card__submit">
                {pages.login.continue}
              </Link>
              <button type="button" className="btn btn--ghost" onClick={onLogout}>
                {common.logout}
              </button>
            </div>
          ) : (
            <form className="login-card__sso-block" onSubmit={onSubmit}>
              <p className="login-card__desc">{pages.login.desc}</p>
              <label className="login-card__label">
                {pages.login.username}
                <input
                  className="login-card__input"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  required
                />
              </label>
              <label className="login-card__label">
                {pages.login.password}
                <input
                  className="login-card__input"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
              </label>
              {error && <p className="login-card__error" role="alert">{error}</p>}
              <button type="submit" className="btn btn--primary login-card__submit" disabled={loading}>
                {loading ? pages.login.submitting : pages.login.submit}
              </button>
            </form>
          )}
        </div>

        <p className="login-page__back">
          <Link to="/">{pages.chat.title}</Link>
        </p>
      </div>
    </div>
  )
}
