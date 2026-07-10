import { Routes, Route } from 'react-router-dom'
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
import { SettingsPage } from './pages/SettingsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<ChatPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/conversations" element={<ConversationsPage />} />
      <Route path="/prompt-templates" element={<PromptTemplatesPage />} />
      <Route path="/models" element={<ModelsPage />} />
      <Route path="/usage" element={<UsagePage />} />
      <Route path="/providers" element={<ProvidersPage />} />
      <Route path="/routing" element={<RoutingPage />} />
      <Route path="/rate-limiting" element={<RateLimitingPage />} />
      <Route path="/observability" element={<ObservabilityPage />} />
      <Route path="/settings" element={<SettingsPage />} />
    </Routes>
  )
}
