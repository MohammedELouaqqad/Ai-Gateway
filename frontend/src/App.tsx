import type { ReactNode } from 'react'
import { Routes, Route } from 'react-router-dom'
import { RequireAuth, RequireRole, AccessDeniedPage } from './auth/RequireAuth'
import { ChatPage } from './pages/ChatPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { DashboardPage } from './pages/DashboardPage'
import { ConversationsPage } from './pages/ConversationsPage'
import { PromptTemplatesPage } from './pages/PromptTemplatesPage'
import { ModelsPage } from './pages/ModelsPage'
import { UsagePage } from './pages/UsagePage'
import { ProvidersPage } from './pages/ProvidersPage'
import { RoutingPage } from './pages/RoutingPage'
import { RateLimitingPage } from './pages/RateLimitingPage'
import { ObservabilityPage } from './pages/ObservabilityPage'
import { AdminHealthPage } from './pages/AdminHealthPage'
import { SettingsPage } from './pages/SettingsPage'

function Authed({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>
}

/** Pages de configuration / supervision plateforme — UI réservée ADMIN. */
function AdminOnly({ children }: { children: ReactNode }) {
  return (
    <Authed>
      <RequireRole role="ADMIN" fallback="/access-denied">
        {children}
      </RequireRole>
    </Authed>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/access-denied" element={<AccessDeniedPage />} />

      <Route path="/" element={<Authed><ChatPage /></Authed>} />
      <Route path="/profile" element={<Authed><ProfilePage /></Authed>} />
      <Route path="/dashboard" element={<Authed><DashboardPage /></Authed>} />
      <Route path="/conversations" element={<Authed><ConversationsPage /></Authed>} />
      <Route path="/prompt-templates" element={<Authed><PromptTemplatesPage /></Authed>} />
      <Route path="/usage" element={<Authed><UsagePage /></Authed>} />
      <Route path="/settings" element={<Authed><SettingsPage /></Authed>} />

      <Route path="/models" element={<AdminOnly><ModelsPage /></AdminOnly>} />
      <Route path="/providers" element={<AdminOnly><ProvidersPage /></AdminOnly>} />
      <Route path="/routing" element={<AdminOnly><RoutingPage /></AdminOnly>} />
      <Route path="/rate-limiting" element={<AdminOnly><RateLimitingPage /></AdminOnly>} />
      <Route path="/observability" element={<AdminOnly><ObservabilityPage /></AdminOnly>} />
      <Route path="/admin/health" element={<AdminOnly><AdminHealthPage /></AdminOnly>} />
    </Routes>
  )
}
