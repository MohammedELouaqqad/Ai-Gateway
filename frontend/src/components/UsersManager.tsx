import { useCallback, useEffect, useState } from 'react'
import { fetchUsers, toggleUserStatus } from '../api/adminUsers'
import type { ApiAdminUser } from '../api/types'
import { useI18n } from '../i18n'
import { formatDateTime } from '../utils/mappers'
import { ApiStateBanner } from './ApiStateBanner'
import { Badge } from './Badge'

export function UsersManager() {
  const { pages, common } = useI18n()
  const t = pages.settings

  const [users, setUsers] = useState<ApiAdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pendingId, setPendingId] = useState<number | null>(null)

  const load = useCallback(async () => {
    const data = await fetchUsers()
    setUsers(data)
    return data
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    load()
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : common.loadError)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [load, common.loadError])

  async function handleToggle(user: ApiAdminUser) {
    setError(null)
    setPendingId(user.id)
    try {
      await toggleUserStatus(user.id)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setPendingId(null)
    }
  }

  return (
    <>
      <p className="settings-section__desc">{t.usersRolesDesc}</p>
      <ApiStateBanner loading={loading} error={error} />

      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">{common.name}</th>
              <th scope="col">{common.description}</th>
              <th scope="col">{t.usersColStatus}</th>
              <th scope="col">{common.created}</th>
              <th scope="col">{common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td><strong>{user.username || '—'}</strong></td>
                <td className="mono">{user.email || '—'}</td>
                <td>
                  {user.enabled ? (
                    <Badge variant="success" dot>{common.active}</Badge>
                  ) : (
                    <Badge variant="secondary" dot>{common.inactive}</Badge>
                  )}
                </td>
                <td className="mono">{formatDateTime(user.createdAt)}</td>
                <td>
                  <button
                    type="button"
                    className={`btn btn--ghost btn--sm${user.enabled ? ' btn--danger' : ''}`}
                    disabled={pendingId === user.id}
                    onClick={() => void handleToggle(user)}
                  >
                    {user.enabled ? t.usersDisable : t.usersEnable}
                  </button>
                </td>
              </tr>
            ))}
            {!loading && users.length === 0 && (
              <tr>
                <td colSpan={5} className="table-empty">{t.usersEmpty}</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}
