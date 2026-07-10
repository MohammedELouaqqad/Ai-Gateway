import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { fetchConversations } from '../api/conversations'
import { fetchModels } from '../api/models'
import { fetchUsageSummary } from '../api/usage'
import type { UsageSummary } from '../api/types'
import { fmt, pages } from '../i18n/fr'
import { isModelDefault } from '../utils/mappers'

export function ProfilePage() {
  const [conversationCount, setConversationCount] = useState(0)
  const [usage, setUsage] = useState<UsageSummary | null>(null)
  const [defaultModelName, setDefaultModelName] = useState('—')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([fetchConversations(), fetchUsageSummary(), fetchModels()])
      .then(([conversations, usageData, models]) => {
        if (cancelled) return
        setConversationCount(conversations.length)
        setUsage(usageData)
        const defaultModel = models.find((m) => isModelDefault(m))
        setDefaultModelName(defaultModel?.displayName ?? defaultModel?.name ?? '—')
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Erreur chargement')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.profile.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{pages.profile.title}</h1>
        <p>{pages.profile.subtitle}</p>
      </header>

      <div className="profile-grid">
        <section className="card profile-card">
          <div className="profile-card__header">
            <div className="profile-card__avatar" aria-hidden="true">U</div>
            <div>
              <h2 className="profile-card__name">Utilisateur test</h2>
              <p className="profile-card__email mono">user id=1 (Keycloak à venir)</p>
            </div>
          </div>
          <dl className="profile-details">
            <div className="profile-details__row">
              <dt>Modèle par défaut</dt>
              <dd><span className="tag tag--model">{defaultModelName}</span></dd>
            </div>
            <div className="profile-details__row">
              <dt>Conversations</dt>
              <dd className="mono">{conversationCount}</dd>
            </div>
            <div className="profile-details__row">
              <dt>Tokens (total)</dt>
              <dd className="mono">{usage ? fmt.num(usage.totalTokens) : '—'}</dd>
            </div>
          </dl>
          <div className="profile-card__actions">
            <Link to="/login" className="btn btn--danger">Se déconnecter</Link>
          </div>
        </section>

        <section className="card profile-card">
          <div className="card__header">
            <h2 className="card__title">Préférences</h2>
          </div>
          <div className="card__body">
            <p className="empty-state__message">
              Les préférences utilisateur seront disponibles après l&apos;intégration Keycloak.
            </p>
          </div>
        </section>
      </div>
    </AppLayout>
  )
}
