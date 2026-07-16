import { Link } from 'react-router-dom'
import { Badge } from './Badge'
import { LanguageToggle } from './LanguageToggle'
import { ThemeToggle } from './ThemeToggle'
import { getRoles, parseAccessToken } from '../auth/roles'
import { clearAccessToken } from '../auth/session'
import { useI18n } from '../i18n'

interface HeaderProps {
  breadcrumbs: { label: string; href?: string }[]
}

function roleBadgeVariant(role: string): 'info' | 'warning' | 'secondary' {
  if (role === 'ADMIN') return 'warning'
  if (role === 'DEVELOPER' || role === 'USER') return 'info'
  return 'secondary'
}

/** Rôles applicatifs mis en avant ; le reste reste visible sur Paramètres. */
function highlightRoles(roles: string[]): string[] {
  const preferred = ['ADMIN', 'DEVELOPER', 'USER', 'SERVICE_ACCOUNT']
  const highlighted = preferred.filter((r) => roles.includes(r))
  return highlighted.length > 0 ? highlighted : roles.slice(0, 3)
}

export function Header({ breadcrumbs }: HeaderProps) {
  const { common } = useI18n()
  const claims = parseAccessToken()
  const username = claims?.preferred_username?.trim() || '—'
  const initial = username !== '—' ? username.charAt(0).toUpperCase() : '?'
  const roles = highlightRoles(getRoles())

  function onLogout() {
    clearAccessToken()
    window.location.assign('/login')
  }

  return (
    <header className="header">
      <nav className="breadcrumbs" aria-label={common.breadcrumbs}>
        {breadcrumbs.map((crumb, i) => {
          const isLast = i === breadcrumbs.length - 1
          const href = crumb.href ?? (i === 0 ? '/dashboard' : undefined)
          return (
            <span key={`${crumb.label}-${i}`} className="breadcrumbs__item">
              {i > 0 && <span className="breadcrumbs__sep" aria-hidden="true">/</span>}
              {!isLast && href ? (
                <Link to={href} className="breadcrumbs__link">{crumb.label}</Link>
              ) : (
                <span className="breadcrumbs__current" aria-current="page">{crumb.label}</span>
              )}
            </span>
          )
        })}
      </nav>
      <div className="header__actions">
        <div className="header__prefs">
          <LanguageToggle />
          <ThemeToggle />
        </div>
        <div className="header__user-menu">
          <Link
            to="/profile"
            className="header__user-btn"
            aria-label={`${common.profileOf} ${username}`}
          >
            <span className="header__user-avatar" aria-hidden="true">{initial}</span>
            <span className="header__user-meta">
              <span className="header__user-name">{username}</span>
              {roles.length > 0 && (
                <span className="header__user-roles" aria-label={common.roles}>
                  {roles.map((role) => (
                    <Badge key={role} variant={roleBadgeVariant(role)}>
                      {role}
                    </Badge>
                  ))}
                </span>
              )}
            </span>
          </Link>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onLogout}>
            {common.logout}
          </button>
        </div>
      </div>
    </header>
  )
}
