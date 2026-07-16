import { useMemo } from 'react'
import { NavLink } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  BarChart3,
  Boxes,
  FileText,
  Gauge,
  History,
  LayoutDashboard,
  MessageSquare,
  Server,
  Settings,
  Split,
} from 'lucide-react'
import { getRoles, hasRole, isAdmin, parseAccessToken } from '../auth/roles'
import { isAuthenticated } from '../auth/session'
import { useI18n } from '../i18n'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
  badge?: 'mvp' | 'roadmap' | 'admin'
  adminOnly?: boolean
  hideForUser?: boolean
}

function SidebarLink({ to, label, icon: Icon, end, badge, soonLabel }: NavItem & { soonLabel: string }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `sidebar__link${isActive ? ' sidebar__link--active' : ''}`
      }
    >
      <Icon className="sidebar__link-icon" size={18} strokeWidth={1.75} aria-hidden="true" />
      <span className="sidebar__link-text">{label}</span>
      {badge === 'roadmap' && (
        <span className="sidebar__link-badge sidebar__link-badge--roadmap">{soonLabel}</span>
      )}
      {badge === 'admin' && (
        <span className="sidebar__link-badge sidebar__link-badge--roadmap">Admin</span>
      )}
    </NavLink>
  )
}

function canSeeItem(item: NavItem): boolean {
  if (item.adminOnly && !isAdmin()) return false
  if (item.hideForUser && !isAdmin() && !hasRole('DEVELOPER')) return false
  return true
}

export function Sidebar() {
  const { nav, navSections, common } = useI18n()
  const authed = isAuthenticated()
  const username = parseAccessToken()?.preferred_username?.trim()
  const appRoles = ['ADMIN', 'DEVELOPER', 'USER', 'SERVICE_ACCOUNT'].filter((r) =>
    getRoles().includes(r),
  )
  const brandSubtitle =
    username
    || (appRoles.length > 0 ? appRoles.join(' · ') : null)
    || (authed ? common.sessionActive : common.notConnected)

  const navGroups = useMemo(
    () => [
      {
        title: navSections.principal,
        items: [
          { to: '/', label: nav.chat, icon: MessageSquare, end: true, badge: 'mvp' as const },
          { to: '/conversations', label: nav.conversations, icon: History, badge: 'mvp' as const },
          { to: '/prompt-templates', label: nav.templates, icon: FileText, badge: 'mvp' as const },
        ],
      },
      {
        title: navSections.supervision,
        items: [
          { to: '/dashboard', label: nav.dashboard, icon: LayoutDashboard, badge: 'mvp' as const },
          { to: '/usage', label: nav.usage, icon: BarChart3, badge: 'mvp' as const },
          {
            to: '/observability',
            label: nav.observability,
            icon: Activity,
            badge: 'roadmap' as const,
            adminOnly: true,
          },
        ],
      },
      {
        title: navSections.configuration,
        items: [
          { to: '/models', label: nav.models, icon: Boxes, badge: 'mvp' as const, adminOnly: true },
          { to: '/providers', label: nav.providers, icon: Server, badge: 'mvp' as const, adminOnly: true },
          {
            to: '/routing',
            label: nav.routing,
            icon: Split,
            badge: 'roadmap' as const,
            adminOnly: true,
          },
          {
            to: '/rate-limiting',
            label: nav.rateLimit,
            icon: Gauge,
            badge: 'roadmap' as const,
            adminOnly: true,
          },
        ],
      },
    ],
    [nav, navSections],
  )

  return (
    <aside className="sidebar" aria-label="Navigation">
      <div className="sidebar__brand">
        <div className="sidebar__logo" aria-hidden="true">AI</div>
        <div className="sidebar__brand-text">
          <div className="sidebar__title">AI Gateway</div>
          <div className="sidebar__subtitle" title={appRoles.join(', ') || undefined}>
            {brandSubtitle}
          </div>
        </div>
      </div>

      <nav className="sidebar__nav" aria-label="Menu">
        {navGroups.map((group) => {
          const items = group.items.filter(canSeeItem)
          if (items.length === 0) return null
          return (
            <div key={group.title} className="sidebar__group">
              <div className="sidebar__section-label">{group.title}</div>
              <div className="sidebar__group-items">
                {items.map((item) => (
                  <SidebarLink key={item.to} {...item} soonLabel={common.soon} />
                ))}
              </div>
            </div>
          )
        })}
      </nav>

      <div className="sidebar__bottom">
        <NavLink
          to="/settings"
          className={({ isActive }) =>
            `sidebar__link${isActive ? ' sidebar__link--active' : ''}`
          }
        >
          <Settings className="sidebar__link-icon" size={18} strokeWidth={1.75} aria-hidden="true" />
          <span className="sidebar__link-text">{nav.settings}</span>
        </NavLink>
      </div>

      <div className="sidebar__footer">
        <div className="sidebar__footer-links">
          <NavLink to="/profile" className="sidebar__footer-link">{nav.profile}</NavLink>
          {!authed && (
            <NavLink to="/login" className="sidebar__footer-link">{nav.login}</NavLink>
          )}
        </div>
      </div>
    </aside>
  )
}
