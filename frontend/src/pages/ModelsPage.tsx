import { useCallback, useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { ModelRowActions } from '../components/ModelRowActions'
import { fetchProviders } from '../api/providers'
import { createModel, deleteModel, fetchModels, setDefaultModel, toggleModelStatus, updateModel } from '../api/models'
import type { ApiModel, ApiProvider } from '../api/types'
import { pages } from '../i18n/fr'
import { isModelDefault, providerToUiType } from '../utils/mappers'

const emptyForm = {
  displayName: '',
  name: '',
  providerId: '',
}

export function ModelsPage() {
  const [models, setModels] = useState<ApiModel[]>([])
  const [providers, setProviders] = useState<ApiProvider[]>([])
  const [form, setForm] = useState(emptyForm)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadModels = useCallback(async () => {
    const data = await fetchModels()
    setModels(data)
    return data
  }, [])

  const loadPage = useCallback(async () => {
    const [modelData, providerData] = await Promise.all([fetchModels(), fetchProviders()])
    setModels(modelData)
    setProviders(providerData)
    if (!form.providerId && providerData.length > 0) {
      setForm((prev) => ({ ...prev, providerId: String(providerData[0].id) }))
    }
    return modelData
  }, [form.providerId])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    loadPage()
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
  }, [loadPage])

  async function handleToggle(id: number) {
    setError(null)
    try {
      await toggleModelStatus(id)
      await loadModels()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur mise à jour')
    }
  }

  async function handleSetDefault(id: number) {
    setError(null)
    try {
      await setDefaultModel(id)
      await loadModels()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur définition défaut')
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!form.displayName.trim() || !form.name.trim() || !form.providerId) return

    setSaving(true)
    setError(null)
    try {
      await createModel({
        displayName: form.displayName.trim(),
        name: form.name.trim(),
        providerId: Number(form.providerId),
        enabled: false,
        isDefault: false,
      })
      setForm((prev) => ({ ...emptyForm, providerId: prev.providerId }))
      await loadPage()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erreur création modèle')
    } finally {
      setSaving(false)
    }
  }

  async function handleRename(model: ApiModel) {
    const displayName = window.prompt('Nom affiché', model.displayName ?? model.name)
    if (!displayName?.trim()) return
    const name = window.prompt('Nom API', model.name)
    if (!name?.trim()) return

    setError(null)
    try {
      await updateModel(model.id, {
        displayName: displayName.trim(),
        name: name.trim(),
      })
      await loadPage()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur renommage')
    }
  }

  async function handleDelete(id: number, label: string) {
    if (!window.confirm(`Supprimer le modèle « ${label} » ?`)) return

    setError(null)
    try {
      await deleteModel(id)
      await loadPage()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur suppression')
    }
  }

  const defaultModel = models.find((m) => isModelDefault(m))

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.models.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{pages.models.title}</h1>
        <p>{pages.models.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">Ajouter un modèle</h2>
          <p className="card__subtitle" style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.875rem' }}>
            Les nouveaux modèles sont créés inactifs — activez-les ensuite dans le tableau.
          </p>
        </div>
        <div className="card__body">
          <form className="form-row" onSubmit={(e) => void handleCreate(e)} aria-label="Créer un modèle">
            <div className="form-group">
              <label className="form-label" htmlFor="model-display-name">Nom affiché</label>
              <input
                id="model-display-name"
                className="form-input"
                value={form.displayName}
                onChange={(e) => setForm((prev) => ({ ...prev, displayName: e.target.value }))}
                placeholder="Qwen 0.5B"
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="model-name">Nom API (Ollama)</label>
              <input
                id="model-name"
                className="form-input mono"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="qwen2.5:0.5b"
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="model-provider">Provider</label>
              <select
                id="model-provider"
                className="form-select"
                value={form.providerId}
                onChange={(e) => setForm((prev) => ({ ...prev, providerId: e.target.value }))}
                required
              >
                {providers.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
            <div className="form-group" style={{ alignSelf: 'end' }}>
              <button type="submit" className="btn btn--primary" disabled={saving || providers.length === 0}>
                {saving ? 'Création…' : '+ Ajouter'}
              </button>
            </div>
          </form>
        </div>
      </section>

      <section className="section">
        <div className="section__header">
          <div>
            <h2 className="section__title">Modèles disponibles</h2>
            <p className="section__subtitle">
              {models.filter((m) => m.enabled && m.provider?.enabled).length} utilisables
              {' · '}
              {models.filter((m) => m.enabled).length} modèles actifs
              {defaultModel ? ` · défaut : ${defaultModel.displayName ?? defaultModel.name}` : ''}
            </p>
          </div>
        </div>

        <div className="card">
          <div className="table-wrapper">
            <table className="data-table data-table--models">
              <thead>
                <tr>
                  <th scope="col">Modèle</th>
                  <th scope="col">Fournisseur</th>
                  <th scope="col">Type</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Défaut</th>
                  <th scope="col" className="data-table__actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {models.map((m) => {
                  const type = providerToUiType(m.provider)
                  const isDefault = isModelDefault(m)

                  return (
                    <tr key={m.id}>
                      <td>
                        <span className="tag tag--model">{m.displayName ?? m.name}</span>
                        <div className="mono" style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{m.name}</div>
                      </td>
                      <td>{m.provider?.name ?? '—'}</td>
                      <td>
                        <span className={`tag ${type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
                          {type === 'onprem' ? 'LOCAL' : 'CLOUD'}
                        </span>
                      </td>
                      <td>
                        <Badge variant={m.enabled && m.provider?.enabled ? 'success' : 'secondary'} dot>
                          {m.enabled && m.provider?.enabled
                            ? 'Actif'
                            : m.enabled && !m.provider?.enabled
                              ? 'Provider off'
                              : 'Inactif'}
                        </Badge>
                      </td>
                      <td>
                        {isDefault ? (
                          <Badge variant="info">Par défaut</Badge>
                        ) : (
                          <span className="mono" style={{ color: 'var(--text-muted)' }}>—</span>
                        )}
                      </td>
                      <td className="data-table__actions-cell">
                        <ModelRowActions
                          model={m}
                          isDefault={isDefault}
                          providerEnabled={Boolean(m.provider?.enabled)}
                          onRename={() => void handleRename(m)}
                          onSetDefault={() => void handleSetDefault(m.id)}
                          onToggle={() => void handleToggle(m.id)}
                          onDelete={() => void handleDelete(m.id, m.displayName ?? m.name)}
                        />
                      </td>
                    </tr>
                  )
                })}
                {!loading && models.length === 0 && (
                  <tr>
                    <td colSpan={6} className="table-empty">Aucun modèle configuré</td>
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
