import { Link } from 'react-router-dom'
import { getAppEnv, envBadgeClass } from '../utils/env'

interface HeaderProps {
  breadcrumbs: { label: string; href?: string }[]
}

export function Header({ breadcrumbs }: HeaderProps) {
  const env = getAppEnv()

  return (
    <header className="header">
      <nav className="breadcrumbs" aria-label="Fil d'Ariane">
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
        <span className={envBadgeClass(env)} aria-label={`Environnement ${env}`}>{env}</span>
        <div className="header__user-menu">
          <Link to="/profile" className="header__user-btn" aria-label="Mon profil">
            <span className="header__user-avatar" aria-hidden="true">U</span>
            <span className="header__user-name">Utilisateur</span>
          </Link>
          <Link to="/login" className="btn btn--ghost btn--sm">Déconnexion</Link>
        </div>
      </div>
    </header>
  )
}
