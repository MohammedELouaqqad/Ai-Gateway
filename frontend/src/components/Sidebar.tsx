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
import { nav, navSections } from '../i18n/fr'
import { getAppEnv } from '../utils/env'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
  badge?: 'mvp' | 'roadmap'
}

const navGroups: { title: string; items: NavItem[] }[] = [
  {
    title: navSections.principal,
    items: [
      { to: '/', label: nav.chat, icon: MessageSquare, end: true, badge: 'mvp' },
      { to: '/conversations', label: nav.conversations, icon: History, badge: 'mvp' },
      { to: '/prompt-templates', label: nav.templates, icon: FileText, badge: 'mvp' },
    ],
  },
  {
    title: navSections.supervision,
    items: [
      { to: '/dashboard', label: nav.dashboard, icon: LayoutDashboard, badge: 'mvp' },
      { to: '/usage', label: nav.usage, icon: BarChart3, badge: 'mvp' },
      { to: '/observability', label: nav.observability, icon: Activity, badge: 'roadmap' },
    ],
  },
  {
    title: navSections.configuration,
    items: [
      { to: '/models', label: nav.models, icon: Boxes, badge: 'mvp' },
      { to: '/providers', label: nav.providers, icon: Server, badge: 'mvp' },
      { to: '/routing', label: nav.routing, icon: Split, badge: 'roadmap' },
      { to: '/rate-limiting', label: nav.rateLimit, icon: Gauge, badge: 'roadmap' },
    ],
  },
]

function SidebarLink({ to, label, icon: Icon, end, badge }: NavItem) {
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
        <span className="sidebar__link-badge sidebar__link-badge--roadmap">Soon</span>
      )}
    </NavLink>
  )
}

export function Sidebar() {
  const env = getAppEnv()

  return (
    <aside className="sidebar" aria-label="Navigation principale">
      <div className="sidebar__brand">
        <div className="sidebar__logo" aria-hidden="true">AI</div>
        <div className="sidebar__brand-text">
          <div className="sidebar__title">AI Gateway</div>
          <div className="sidebar__subtitle">Admin</div>
        </div>
      </div>

      <nav className="sidebar__nav" aria-label="Menu">
        {navGroups.map((group) => (
          <div key={group.title} className="sidebar__group">
            <div className="sidebar__section-label">{group.title}</div>
            <div className="sidebar__group-items">
              {group.items.map((item) => (
                <SidebarLink key={item.to} {...item} />
              ))}
            </div>
          </div>
        ))}
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
          <NavLink to="/profile" className="sidebar__footer-link">Mon profil</NavLink>
          <NavLink to="/login" className="sidebar__footer-link">Connexion</NavLink>
        </div>
        <div className="sidebar__footer-meta">
          <span className="sidebar__status-dot" aria-hidden="true" />
          v1.0 · {env.toLowerCase()}
        </div>
      </div>
    </aside>
  )
}
