import { useCallback, useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { isAdmin } from '../auth/roles'
import {
  createProvider,
  deleteProvider,
  fetchProviders,
  toggleProviderStatus,
  updateProvider,
} from '../api/providers'
import type { ApiProvider, ProviderLocation, ProviderType } from '../api/types'
import { useI18n } from '../i18n'
import { providerToUiType } from '../utils/mappers'

const emptyProviderForm = {
  name: '',
  baseUrl: 'http://localhost:11434',
  type: 'OLLAMA' as ProviderType,
  location: 'LOCAL' as ProviderLocation,
  priority: '1',
  apiKey: '',
}

function ProviderRow({
  provider,
  canMutate,
  onToggle,
  onRename,
  onDelete,
}: {
  provider: ApiProvider
  canMutate: boolean
  onToggle: (id: number) => void
  onRename: (provider: ApiProvider) => void
  onDelete: (id: number, name: string) => void
}) {
  const { common, pages } = useI18n()
  const type = providerToUiType(provider)
  const healthVariant = provider.enabled ? 'success' : 'secondary'
  return (
    <tr>
      <td>
        <strong>{provider.name}</strong>
        {provider.baseUrl && (
          <div className="mono" style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            {provider.baseUrl}
          </div>
        )}
      </td>
      <td>
        <span className={`tag ${type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
          {type === 'onprem' ? 'LOCAL' : 'CLOUD'}
        </span>
      </td>
      <td>
        <Badge variant={healthVariant} dot>
          {provider.enabled ? common.active : common.inactive}
        </Badge>
      </td>
      <td className="mono">{provider.priority ?? '—'}</td>
      <td>{provider.location === 'LOCAL' ? pages.providers.internal : pages.providers.cloud}</td>
      <td className="mono" style={{ fontSize: '0.75rem' }}>{provider.type}</td>
      <td>
        {provider.apiKeyPresent ? (
          <Badge variant="success" dot>{pages.providers.apiKeyPresent}</Badge>
        ) : (
          <Badge variant="secondary" dot>{pages.providers.apiKeyAbsent}</Badge>
        )}
      </td>
      {canMutate && (
        <td>
          <div className="table-actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => onRename(provider)}
            >
              {common.rename}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => onToggle(provider.id)}
            >
              {provider.enabled ? common.disable : common.enable}
            </button>
            <button
              type="button"
              className="btn btn--ghost btn--sm btn--danger"
              onClick={() => onDelete(provider.id, provider.name)}
            >
              {common.delete}
            </button>
          </div>
        </td>
      )}
    </tr>
  )
}

export function ProvidersPage() {
  const { pages, common } = useI18n()
  const p = pages.providers
  const [providers, setProviders] = useState<ApiProvider[]>([])
  const [form, setForm] = useState(emptyProviderForm)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadProviders = useCallback(async () => {
    const data = await fetchProviders()
    setProviders(data)
    return data
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    loadProviders()
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : common.loadError)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [loadProviders, common.loadError])

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim() || !form.baseUrl.trim()) return

    setSaving(true)
    setError(null)
    try {
      await createProvider({
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        type: form.type,
        location: form.location,
        enabled: false,
        priority: Number(form.priority) || 1,
        apiKey: form.apiKey.trim() || undefined,
      })
      setForm(emptyProviderForm)
      await loadProviders()
    } catch (err) {
      setError(err instanceof Error ? err.message : common.loadError)
    } finally {
      setSaving(false)
    }
  }

  async function handleToggle(id: number) {
    setError(null)
    try {
      await toggleProviderStatus(id)
      await loadProviders()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  async function handleRename(provider: ApiProvider) {
    const name = window.prompt(p.renameNamePrompt, provider.name)
    if (!name?.trim()) return
    const baseUrl = window.prompt(p.renameUrlPrompt, provider.baseUrl ?? '')
    if (baseUrl === null) return
    // Always starts blank — the stored key is never re-displayed. Leaving it blank
    // here means "don't touch the key", matching the backend's blank/null semantics.
    const apiKey = window.prompt(p.renameApiKeyPrompt, '')
    if (apiKey === null) return

    setError(null)
    try {
      await updateProvider(provider.id, {
        name: name.trim(),
        baseUrl: baseUrl.trim() || undefined,
        priority: provider.priority,
        apiKey: apiKey.trim() || undefined,
      })
      await loadProviders()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  async function handleDelete(id: number, name: string) {
    if (!window.confirm(p.deleteConfirm.replace('{name}', name))) return

    setError(null)
    try {
      await deleteProvider(id)
      await loadProviders()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  const localCount = providers.filter((pr) => providerToUiType(pr) === 'onprem').length
  const activeCount = providers.filter((pr) => pr.enabled).length
  const canMutate = isAdmin()

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: p.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{p.title}</h1>
        <p>{p.subtitle}</p>
        {!canMutate && (
          <p className="section__subtitle" style={{ marginTop: '0.5rem' }}>
            {common.readOnly}
          </p>
        )}
      </header>

      <div className="sovereignty-card" role="region" aria-label={p.sovereignty}>
        <div className="sovereignty-card__content">
          <h2>{p.sovereignty}</h2>
          <p>{p.sovereigntyDesc}</p>
        </div>
        <div className="sovereignty-card__stats">
          <div>
            <div className="sovereignty-card__stat-value">{localCount}</div>
            <div className="sovereignty-card__stat-label">{p.localEngines}</div>
          </div>
          <div>
            <div className="sovereignty-card__stat-value">{activeCount}</div>
            <div className="sovereignty-card__stat-label">{p.actives}</div>
          </div>
        </div>
      </div>

      {canMutate && (
      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{p.addTitle}</h2>
          <p className="card__subtitle" style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            {p.addHint}
          </p>
        </div>
        <div className="card__body">
          <form className="form-row" onSubmit={(e) => void handleCreate(e)} aria-label={p.createAria}>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-name">{common.name}</label>
              <input
                id="provider-name"
                className="form-input"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder={p.namePlaceholder}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-url">{p.baseUrl}</label>
              <input
                id="provider-url"
                className="form-input mono"
                value={form.baseUrl}
                onChange={(e) => setForm((prev) => ({ ...prev, baseUrl: e.target.value }))}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-type">{common.apiType}</label>
              <select
                id="provider-type"
                className="form-select"
                value={form.type}
                onChange={(e) => setForm((prev) => ({ ...prev, type: e.target.value as ProviderType }))}
              >
                <option value="OLLAMA">OLLAMA</option>
                <option value="OPENAI">OPENAI</option>
                <option value="AZURE_OPENAI">AZURE_OPENAI</option>
                <option value="SGLANG">SGLANG</option>
                <option value="VLLM">VLLM</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-apikey">{p.apiKeyLabel}</label>
              <input
                id="provider-apikey"
                type="password"
                className="form-input"
                value={form.apiKey}
                onChange={(e) => setForm((prev) => ({ ...prev, apiKey: e.target.value }))}
                placeholder={p.apiKeyPlaceholder}
                autoComplete="new-password"
              />
              <span className="form-hint">{p.apiKeyHint}</span>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-location">{common.location}</label>
              <select
                id="provider-location"
                className="form-select"
                value={form.location}
                onChange={(e) => setForm((prev) => ({ ...prev, location: e.target.value as ProviderLocation }))}
              >
                <option value="LOCAL">LOCAL</option>
                <option value="CLOUD">CLOUD</option>
              </select>
            </div>
            <div className="form-group" style={{ alignSelf: 'end' }}>
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? common.creating : common.add}
              </button>
            </div>
          </form>
        </div>
      </section>
      )}

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">{p.title}</h2>
            <p className="section__subtitle">
              {p.listMeta
                .replace('{n}', String(providers.length))
                .replace('{active}', String(activeCount))
                .replace('{local}', String(localCount))}
            </p>
          </div>
        </div>

        <div className="card">
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{common.name}</th>
                  <th scope="col">{common.type}</th>
                  <th scope="col">{common.status}</th>
                  <th scope="col">{common.priority}</th>
                  <th scope="col">{common.location}</th>
                  <th scope="col">{common.apiType}</th>
                  <th scope="col">{p.apiKeyLabel}</th>
                  {canMutate && <th scope="col">{common.actions}</th>}
                </tr>
              </thead>
              <tbody>
                {providers.map((provider) => (
                  <ProviderRow
                    key={provider.id}
                    provider={provider}
                    canMutate={canMutate}
                    onToggle={(id) => void handleToggle(id)}
                    onRename={(pr) => void handleRename(pr)}
                    onDelete={(id, name) => void handleDelete(id, name)}
                  />
                ))}
                {!loading && providers.length === 0 && (
                  <tr>
                    <td colSpan={canMutate ? 8 : 7} className="table-empty">{p.empty}</td>
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
