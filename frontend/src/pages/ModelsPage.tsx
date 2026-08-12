import { useCallback, useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { ModelRowActions } from '../components/ModelRowActions'
import { isAdmin } from '../auth/roles'
import { fetchProviders } from '../api/providers'
import { createModel, deleteModel, fetchModels, setDefaultModel, toggleModelStatus, updateModel } from '../api/models'
import type { ApiModel, ApiProvider } from '../api/types'
import { useI18n } from '../i18n'
import { isModelDefault, providerToUiType } from '../utils/mappers'

const emptyForm = {
  displayName: '',
  name: '',
  providerId: '',
}

export function ModelsPage() {
  const { pages, common } = useI18n()
  const modelsCopy = pages.models
  const [models, setModels] = useState<ApiModel[]>([])
  const [providers, setProviders] = useState<ApiProvider[]>([])
  const [form, setForm] = useState(emptyForm)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editForm, setEditForm] = useState(emptyForm)

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
          setError(e instanceof Error ? e.message : common.loadError)
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

  function startEdit(model: ApiModel) {
    setError(null)
    setEditingId(model.id)
    setEditForm({
      displayName: model.displayName ?? model.name,
      name: model.name,
      providerId: model.provider ? String(model.provider.id) : '',
    })
  }

  function cancelEdit() {
    setEditingId(null)
  }

  async function handleSaveEdit(id: number) {
    if (!editForm.displayName.trim() || !editForm.name.trim() || !editForm.providerId) return

    setError(null)
    try {
      await updateModel(id, {
        displayName: editForm.displayName.trim(),
        name: editForm.name.trim(),
        providerId: Number(editForm.providerId),
      })
      setEditingId(null)
      await loadPage()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  async function handleDelete(id: number, label: string) {
    if (!window.confirm(modelsCopy.deleteConfirm.replace('{name}', label))) return

    setError(null)
    try {
      await deleteModel(id)
      await loadPage()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  const defaultModel = models.find((m) => isModelDefault(m))
  const canMutate = isAdmin()

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: modelsCopy.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{modelsCopy.title}</h1>
        <p>{modelsCopy.subtitle}</p>
        {!canMutate && (
          <p className="section__subtitle" style={{ marginTop: '0.5rem' }}>
            {common.readOnly}
          </p>
        )}
      </header>

      {canMutate && (
        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{modelsCopy.addTitle}</h2>
            <p className="card__subtitle" style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.875rem' }}>
              {modelsCopy.addHint}
            </p>
          </div>
          <div className="card__body">
            <form className="form-row" onSubmit={(e) => void handleCreate(e)} aria-label={modelsCopy.createAria}>
              <div className="form-group">
                <label className="form-label" htmlFor="model-display-name">{modelsCopy.displayName}</label>
                <input
                  id="model-display-name"
                  className="form-input"
                  value={form.displayName}
                  onChange={(e) => setForm((prev) => ({ ...prev, displayName: e.target.value }))}
                  placeholder={modelsCopy.displayNamePlaceholder}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="model-name">{modelsCopy.apiName}</label>
                <input
                  id="model-name"
                  className="form-input mono"
                  value={form.name}
                  onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                  placeholder={modelsCopy.apiNamePlaceholder}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="model-provider">{common.provider}</label>
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
            <h2 className="section__title">{modelsCopy.listTitle}</h2>
            <p className="section__subtitle">
              {modelsCopy.listMeta
                .replace('{usable}', String(models.filter((m) => m.enabled && m.provider?.enabled).length))
                .replace('{active}', String(models.filter((m) => m.enabled).length))}
              {defaultModel
                ? modelsCopy.listDefault.replace('{name}', defaultModel.displayName ?? defaultModel.name)
                : ''}
            </p>
          </div>
        </div>

        <div className="card">
          <div className="table-wrapper">
            <table className="data-table data-table--models">
              <thead>
                <tr>
                  <th scope="col">{common.model}</th>
                  <th scope="col">{common.provider}</th>
                  <th scope="col">{common.type}</th>
                  <th scope="col">{common.status}</th>
                  <th scope="col">{common.default}</th>
                  {canMutate && (
                    <th scope="col" className="data-table__actions-head">{common.actions}</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {models.map((m) => {
                  const type = providerToUiType(m.provider)
                  const isDefault = isModelDefault(m)

                  if (editingId === m.id) {
                    return (
                      <tr key={m.id} className="data-table__row--editing">
                        <td>
                          <div className="data-table__field-stack">
                            <input
                              className="form-input"
                              aria-label={modelsCopy.displayName}
                              value={editForm.displayName}
                              onChange={(e) => setEditForm((prev) => ({ ...prev, displayName: e.target.value }))}
                            />
                            <input
                              className="form-input mono"
                              aria-label={modelsCopy.apiName}
                              value={editForm.name}
                              onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                            />
                          </div>
                        </td>
                        <td>
                          <select
                            className="form-select"
                            aria-label={common.provider}
                            value={editForm.providerId}
                            onChange={(e) => setEditForm((prev) => ({ ...prev, providerId: e.target.value }))}
                          >
                            {providers.map((p) => (
                              <option key={p.id} value={p.id}>{p.name}</option>
                            ))}
                          </select>
                        </td>
                        <td colSpan={3} className="mono" style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                          {modelsCopy.editStatusHint}
                        </td>
                        {canMutate && (
                          <td className="data-table__actions-cell">
                            <div className="table-actions">
                              <button
                                type="button"
                                className="btn btn--primary btn--sm"
                                onClick={() => void handleSaveEdit(m.id)}
                              >
                                {common.save}
                              </button>
                              <button type="button" className="btn btn--ghost btn--sm" onClick={cancelEdit}>
                                {common.cancel}
                              </button>
                            </div>
                          </td>
                        )}
                      </tr>
                    )
                  }

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
                            ? common.active
                            : m.enabled && !m.provider?.enabled
                              ? common.providerOff
                              : common.inactive}
                        </Badge>
                      </td>
                      <td>
                        {isDefault ? (
                          <Badge variant="info">{common.byDefault}</Badge>
                        ) : (
                          <span className="mono" style={{ color: 'var(--text-muted)' }}>—</span>
                        )}
                      </td>
                      {canMutate && (
                        <td className="data-table__actions-cell">
                          <ModelRowActions
                            model={m}
                            isDefault={isDefault}
                            providerEnabled={Boolean(m.provider?.enabled)}
                            canMutate={canMutate}
                            onRename={() => startEdit(m)}
                            onSetDefault={() => void handleSetDefault(m.id)}
                            onToggle={() => void handleToggle(m.id)}
                            onDelete={() => void handleDelete(m.id, m.displayName ?? m.name)}
                          />
                        </td>
                      )}
                    </tr>
                  )
                })}
                {!loading && models.length === 0 && (
                  <tr>
                    <td colSpan={canMutate ? 6 : 5} className="table-empty">{modelsCopy.empty}</td>
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
