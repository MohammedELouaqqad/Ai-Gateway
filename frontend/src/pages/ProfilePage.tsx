import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Copy, MessagesSquare } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { Badge } from '../components/Badge'
import { apiFetch } from '../api/client'
import { fetchConversations } from '../api/conversations'
import { fetchModels } from '../api/models'
import { clearAccessToken } from '../auth/session'
import { useI18n } from '../i18n'
import { isModelDefault } from '../utils/mappers'

interface Profile {
  id: number
  keycloakId: string
  username: string
  email: string
  enabled: boolean
}

function truncateMiddle(value: string, head = 10, tail = 8): string {
  if (value.length <= head + tail + 1) return value
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

export function ProfilePage() {
  const { pages, fmt } = useI18n()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [conversationCount, setConversationCount] = useState(0)
  const [defaultModelName, setDefaultModelName] = useState('—')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([
      apiFetch<Profile>('/profile'),
      fetchConversations(),
      fetchModels(),
    ])
      .then(([me, conversations, models]) => {
        if (cancelled) return
        setProfile(me)
        setConversationCount(conversations.length)
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

  function onLogout() {
    clearAccessToken()
    window.location.assign('/login')
  }

  async function copyKeycloakId() {
    const value = profile?.keycloakId
    if (!value) return
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setError('Impossible de copier l’identifiant Keycloak')
    }
  }

  const displayName = profile?.username || (loading ? '—' : 'Utilisateur')
  const initial = displayName !== '—' ? displayName.charAt(0).toUpperCase() : '?'
  const email = profile?.email || '—'
  const keycloakId = profile?.keycloakId || '—'
  const userId = profile?.id != null ? String(profile.id) : '—'
  const enabled = profile?.enabled === true

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.profile.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{pages.profile.title}</h1>
        <p>{pages.profile.subtitle}</p>
      </header>

      <section className="card profile-card" aria-labelledby="profile-identity-heading">
        <div className="profile-card__header">
          <div
            className="profile-card__avatar"
            aria-label={`Avatar de ${displayName}`}
            role="img"
          >
            {initial}
          </div>
          <div className="profile-card__identity">
            <div className="profile-card__identity-row">
              <h2 id="profile-identity-heading" className="profile-card__name">
                {displayName}
              </h2>
              {!loading && profile && (
                <Badge variant={enabled ? 'success' : 'secondary'} dot>
                  {enabled ? pages.profile.statusActive : pages.profile.statusInactive}
                </Badge>
              )}
            </div>
            <p className="profile-card__email mono">{email}</p>
          </div>
        </div>
      </section>

      <section className="profile-summary" aria-label="Activité">
        <div className="kpi-grid profile-kpi-grid">
          <article className="kpi-card">
            <div className="kpi-card__header">
              <span className="kpi-card__label">{pages.profile.kpiConversations}</span>
              <span className="kpi-card__icon" aria-hidden="true">
                <MessagesSquare size={16} strokeWidth={1.75} />
              </span>
            </div>
            <div className="kpi-card__value">
              {loading ? '—' : fmt.num(conversationCount)}
            </div>
          </article>
        </div>
        <p className="profile-summary__link">
          <Link to="/usage" className="card__link">
            {pages.profile.viewUsage}
          </Link>
        </p>
      </section>

      <section className="card profile-account" aria-labelledby="profile-account-heading">
        <div className="card__header">
          <h2 id="profile-account-heading" className="card__title">
            {pages.profile.accountSection}
          </h2>
        </div>
        <div className="card__body">
          <dl className="profile-details">
            <div className="profile-details__row">
              <dt>{pages.profile.accountId}</dt>
              <dd className="mono">{userId}</dd>
            </div>
            <div className="profile-details__row">
              <dt>{pages.profile.accountDefaultModel}</dt>
              <dd>{loading ? '—' : defaultModelName}</dd>
            </div>
            <div className="profile-details__row profile-details__row--keycloak">
              <dt>{pages.profile.accountKeycloakSub}</dt>
              <dd className="profile-keycloak">
                <code
                  className="profile-keycloak__value mono"
                  title={keycloakId !== '—' ? keycloakId : undefined}
                  aria-label={
                    keycloakId !== '—'
                      ? `Keycloak sub : ${keycloakId}`
                      : 'Keycloak sub indisponible'
                  }
                >
                  {keycloakId === '—' ? '—' : truncateMiddle(keycloakId)}
                </code>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm profile-keycloak__copy"
                  onClick={() => void copyKeycloakId()}
                  disabled={loading || !profile?.keycloakId}
                  aria-label={copied ? pages.profile.copied : `${pages.profile.copy} Keycloak sub`}
                >
                  <Copy size={14} strokeWidth={1.75} aria-hidden="true" />
                  {copied ? pages.profile.copied : pages.profile.copy}
                </button>
              </dd>
            </div>
          </dl>
        </div>
        <div className="profile-card__actions">
          <Link to="/settings" className="btn btn--ghost">
            {pages.profile.settings}
          </Link>
          <button type="button" className="btn btn--ghost btn--danger" onClick={onLogout}>
            {pages.profile.logout}
          </button>
        </div>
      </section>
    </AppLayout>
  )
}
