import { useCallback, useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import {
  createProvider,
  deleteProvider,
  fetchProviders,
  toggleProviderStatus,
  updateProvider,
} from '../api/providers'
import type { ApiProvider, ProviderLocation, ProviderType } from '../api/types'
import { pages } from '../i18n/fr'
import { providerToUiType } from '../utils/mappers'

const emptyProviderForm = {
  name: '',
  baseUrl: 'http://localhost:11434',
  type: 'OLLAMA' as ProviderType,
  location: 'LOCAL' as ProviderLocation,
  priority: '1',
}

function ProviderRow({
  provider,
  onToggle,
  onRename,
  onDelete,
}: {
  provider: ApiProvider
  onToggle: (id: number) => void
  onRename: (provider: ApiProvider) => void
  onDelete: (id: number, name: string) => void
}) {
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
          {provider.enabled ? 'Actif' : 'Inactif'}
        </Badge>
      </td>
      <td className="mono">{provider.priority ?? '—'}</td>
      <td>{provider.location === 'LOCAL' ? 'Interne' : 'Cloud'}</td>
      <td className="mono" style={{ fontSize: '0.75rem' }}>{provider.type}</td>
      <td>
        <div className="table-actions">
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => onRename(provider)}
          >
            Renommer
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            onClick={() => onToggle(provider.id)}
          >
            {provider.enabled ? 'Désactiver' : 'Activer'}
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm btn--danger"
            onClick={() => onDelete(provider.id, provider.name)}
          >
            Supprimer
          </button>
        </div>
      </td>
    </tr>
  )
}

export function ProvidersPage() {
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
          setError(e instanceof Error ? e.message : 'Erreur chargement')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [loadProviders])

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
      })
      setForm(emptyProviderForm)
      await loadProviders()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erreur création provider')
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
      setError(e instanceof Error ? e.message : 'Erreur mise à jour')
    }
  }

  async function handleRename(provider: ApiProvider) {
    const name = window.prompt('Nom du fournisseur', provider.name)
    if (!name?.trim()) return
    const baseUrl = window.prompt('URL de base', provider.baseUrl ?? '')
    if (baseUrl === null) return

    setError(null)
    try {
      await updateProvider(provider.id, {
        name: name.trim(),
        baseUrl: baseUrl.trim() || undefined,
        priority: provider.priority,
      })
      await loadProviders()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur renommage')
    }
  }

  async function handleDelete(id: number, name: string) {
    if (!window.confirm(`Supprimer le fournisseur « ${name} » ?`)) return

    setError(null)
    try {
      await deleteProvider(id)
      await loadProviders()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur suppression')
    }
  }

  const localCount = providers.filter((p) => providerToUiType(p) === 'onprem').length
  const activeCount = providers.filter((p) => p.enabled).length

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.providers.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{pages.providers.title}</h1>
        <p>{pages.providers.subtitle}</p>
      </header>

      <div className="sovereignty-card" role="region" aria-label="Souveraineté">
        <div className="sovereignty-card__content">
          <h2>Souveraineté</h2>
          <p>Catalogue documenté (Ollama, OpenAI, Azure, SGLang, vLLM). Activez les fournisseurs prêts à l&apos;emploi.</p>
        </div>
        <div className="sovereignty-card__stats">
          <div>
            <div className="sovereignty-card__stat-value">{localCount}</div>
            <div className="sovereignty-card__stat-label">Moteurs local</div>
          </div>
          <div>
            <div className="sovereignty-card__stat-value">{activeCount}</div>
            <div className="sovereignty-card__stat-label">Actifs</div>
          </div>
        </div>
      </div>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">Ajouter un fournisseur</h2>
          <p className="card__subtitle" style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            Créé inactif — activez-le dans le tableau une fois configuré.
          </p>
        </div>
        <div className="card__body">
          <form className="form-row" onSubmit={(e) => void handleCreate(e)} aria-label="Créer un fournisseur">
            <div className="form-group">
              <label className="form-label" htmlFor="provider-name">Nom</label>
              <input
                id="provider-name"
                className="form-input"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="Ollama Local"
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-url">URL de base</label>
              <input
                id="provider-url"
                className="form-input mono"
                value={form.baseUrl}
                onChange={(e) => setForm((prev) => ({ ...prev, baseUrl: e.target.value }))}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="provider-type">Type API</label>
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
              <label className="form-label" htmlFor="provider-location">Localisation</label>
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
                {saving ? 'Création…' : '+ Ajouter'}
              </button>
            </div>
          </form>
        </div>
      </section>

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Fournisseurs</h2>
            <p className="section__subtitle">
              {providers.length} configuré(s) · {activeCount} actif(s) · {localCount} local
            </p>
          </div>
        </div>

        <div className="card">
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Nom</th>
                  <th scope="col">Type</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Priorité</th>
                  <th scope="col">Localisation</th>
                  <th scope="col">Type API</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {providers.map((provider) => (
                  <ProviderRow
                    key={provider.id}
                    provider={provider}
                    onToggle={(id) => void handleToggle(id)}
                    onRename={(p) => void handleRename(p)}
                    onDelete={(id, name) => void handleDelete(id, name)}
                  />
                ))}
                {!loading && providers.length === 0 && (
                  <tr>
                    <td colSpan={7} className="table-empty">Aucun provider configuré</td>
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
