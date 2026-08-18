import { Sidebar } from '../components/Sidebar'
import { Header } from '../components/Header'

interface AppLayoutProps {
  breadcrumbs: { label: string; href?: string }[]
  children: React.ReactNode
  variant?: 'default' | 'chat'
}

export function AppLayout({ breadcrumbs, children, variant = 'default' }: AppLayoutProps) {
  return (
    <div className={`app-shell${variant === 'chat' ? ' app-shell--chat' : ''}`}>
      <Sidebar />
      <div className="main-area">
        <Header breadcrumbs={breadcrumbs} />
        <main className={`page-content${variant === 'chat' ? ' page-content--chat' : ''}`}>{children}</main>
      </div>
    </div>
  )
}
