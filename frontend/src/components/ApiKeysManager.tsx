import { useCallback, useEffect, useState } from 'react'
import { createApiKey, fetchApiKeys, revokeApiKey } from '../api/apiKeys'
import type { ApiApiKey, ApiApiKeyCreated } from '../api/types'
import { useI18n } from '../i18n'
import { formatDateTime } from '../utils/mappers'
import { ApiStateBanner } from './ApiStateBanner'
import { Badge } from './Badge'

const NAME_MAX = 100

/** value = days, '' = never expires */
const EXPIRY_OPTIONS = ['', '30', '90', '365'] as const

export function ApiKeysManager() {
  const { pages, common } = useI18n()
  const t = pages.settings

  const [keys, setKeys] = useState<ApiApiKey[]>([])
  const [name, setName] = useState('')
  const [expiresInDays, setExpiresInDays] = useState<(typeof EXPIRY_OPTIONS)[number]>('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [revealed, setRevealed] = useState<ApiApiKeyCreated | null>(null)
  const [copied, setCopied] = useState(false)

  const load = useCallback(async () => {
    const data = await fetchApiKeys()
    setKeys(data)
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

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return
    setSaving(true)
    setError(null)
    try {
      const created = await createApiKey({
        name: trimmed,
        expiresInDays: expiresInDays ? Number(expiresInDays) : undefined,
      })
      setRevealed(created)
      setName('')
      setExpiresInDays('')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setSaving(false)
    }
  }

  async function handleRevoke(key: ApiApiKey) {
    if (!window.confirm(t.apiKeysRevokeConfirm.replace('{name}', key.name))) return
    setError(null)
    try {
      await revokeApiKey(key.id)
      if (revealed?.id === key.id) setRevealed(null)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  async function handleCopy() {
    if (!revealed) return
    try {
      await navigator.clipboard.writeText(revealed.rawKey)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setError('Impossible de copier la clé')
    }
  }

  return (
    <>
      <p className="settings-section__desc">{t.apiKeysDesc}</p>
      <ApiStateBanner loading={loading} error={error} />

      {revealed && (
        <div className="alert-banner alert-banner--warning" role="alert">
          <div className="alert-banner__text">
            <strong>{t.apiKeysRevealTitle}</strong> — {t.apiKeysRevealWarning}
            <div className="api-key-reveal">
              <code className="mono api-key-reveal__value">{revealed.rawKey}</code>
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void handleCopy()}>
                {copied ? common.copied : common.copy}
              </button>
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setRevealed(null)}>
                {common.cancel}
              </button>
            </div>
          </div>
        </div>
      )}

      <form className="form-stack" onSubmit={(e) => void handleCreate(e)}>
        <div className="form-group">
          <label className="form-label" htmlFor="api-key-name">{common.name}</label>
          <div className="table-actions">
            <input
              id="api-key-name"
              className="form-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t.apiKeysNamePlaceholder}
              maxLength={NAME_MAX}
              required
            />
            <select
              id="api-key-expires"
              className="form-input"
              aria-label={t.apiKeysExpiresLabel}
              value={expiresInDays}
              onChange={(e) => setExpiresInDays(e.target.value as (typeof EXPIRY_OPTIONS)[number])}
            >
              <option value="">{t.apiKeysExpiresNever}</option>
              <option value="30">{t.apiKeysExpires30}</option>
              <option value="90">{t.apiKeysExpires90}</option>
              <option value="365">{t.apiKeysExpires365}</option>
            </select>
            <button type="submit" className="btn btn--primary btn--sm" disabled={saving}>
              {saving ? common.creating : common.create}
            </button>
          </div>
        </div>
      </form>

      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">{common.name}</th>
              <th scope="col">{t.apiKeysColPrefix}</th>
              <th scope="col">{common.status}</th>
              <th scope="col">{common.created}</th>
              <th scope="col">{t.apiKeysColExpires}</th>
              <th scope="col">{common.actions}</th>
            </tr>
          </thead>
          <tbody>
            {keys.map((key) => {
              const expired = !!key.expiresAt && new Date(key.expiresAt).getTime() < Date.now()
              return (
                <tr key={key.id}>
                  <td><strong>{key.name}</strong></td>
                  <td className="mono">{key.keyPrefix}…</td>
                  <td>
                    {!key.enabled ? (
                      <Badge variant="secondary" dot>{t.apiKeysRevoked}</Badge>
                    ) : expired ? (
                      <Badge variant="secondary" dot>{t.apiKeysExpiredBadge}</Badge>
                    ) : (
                      <Badge variant="success" dot>{common.active}</Badge>
                    )}
                  </td>
                  <td className="mono">{formatDateTime(key.createdAt)}</td>
                  <td className="mono">{key.expiresAt ? formatDateTime(key.expiresAt) : t.apiKeysExpiresNever}</td>
                  <td>
                    {key.enabled && (
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm btn--danger"
                        onClick={() => void handleRevoke(key)}
                      >
                        {t.apiKeysRevoke}
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
            {!loading && keys.length === 0 && (
              <tr>
                <td colSpan={6} className="table-empty">{t.apiKeysEmpty}</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}
