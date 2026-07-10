import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { fetchConversations } from '../api/conversations'
import type { ApiConversation } from '../api/types'
import { pages } from '../i18n/fr'
import { formatDateTime, providerToUiType } from '../utils/mappers'

export function ConversationsPage() {
  const [conversations, setConversations] = useState<ApiConversation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fetchConversations()
      .then((data) => {
        if (!cancelled) setConversations(data)
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
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.conversations.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{pages.conversations.title}</h1>
          <p>{pages.conversations.subtitle}</p>
        </div>
        <div className="page-header__actions">
          <Link to="/" className="btn btn--primary btn--sm">Ouvrir le chat</Link>
        </div>
      </header>

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Récentes</h2>
            <p className="section__subtitle">{conversations.length} conversation(s)</p>
          </div>
        </div>

        <div className="card">
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Titre</th>
                  <th scope="col">Date</th>
                  <th scope="col">Provider</th>
                  <th scope="col">Type</th>
                  <th scope="col">Modèle</th>
                  <th scope="col">ID</th>
                </tr>
              </thead>
              <tbody>
                {conversations.map((c) => {
                  const type = providerToUiType(c.model?.provider)
                  return (
                    <tr key={c.id}>
                      <td>
                        <Link to="/" className="table-link">
                          <strong>{c.title}</strong>
                        </Link>
                      </td>
                      <td className="mono">{formatDateTime(c.updatedAt ?? c.createdAt)}</td>
                      <td>{c.model?.provider?.name ?? '—'}</td>
                      <td>
                        <span className={`tag ${type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
                          {type === 'onprem' ? 'LOCAL' : 'CLOUD'}
                        </span>
                      </td>
                      <td>
                        <span className="tag tag--model">
                          {c.model?.displayName ?? c.model?.name ?? '—'}
                        </span>
                      </td>
                      <td className="mono">{c.id}</td>
                    </tr>
                  )
                })}
                {!loading && conversations.length === 0 && (
                  <tr>
                    <td colSpan={6} className="table-empty">
                      Aucune conversation —{' '}
                      <Link to="/" className="table-link">démarrer dans le chat</Link>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </AppLayout>
  )
}
