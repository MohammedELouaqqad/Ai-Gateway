import { useCallback, useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import {
  createPromptTemplate,
  deletePromptTemplate,
  fetchPromptTemplates,
  updatePromptTemplate,
} from '../api/promptTemplates'
import type { ApiPromptTemplate } from '../api/types'
import { pages } from '../i18n/fr'
import {
  CONTENT_LIMITS,
  formatCharCount,
  truncatePreview,
} from '../utils/contentLimits'
import { formatDateTime } from '../utils/mappers'

const emptyForm = {
  name: '',
  description: '',
  content: '',
}

export function PromptTemplatesPage() {
  const [templates, setTemplates] = useState<ApiPromptTemplate[]>([])
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadTemplates = useCallback(async () => {
    const data = await fetchPromptTemplates()
    setTemplates(data)
    return data
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    loadTemplates()
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
  }, [loadTemplates])

  function startEdit(template: ApiPromptTemplate) {
    setEditingId(template.id)
    setForm({
      name: template.name,
      description: template.description ?? '',
      content: template.content,
    })
  }

  function resetForm() {
    setEditingId(null)
    setForm(emptyForm)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form.name.trim() || !form.content.trim()) return
    setSaving(true)
    setError(null)
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        content: form.content.trim(),
      }
      if (editingId != null) {
        await updatePromptTemplate(editingId, payload)
      } else {
        await createPromptTemplate(payload)
      }
      resetForm()
      await loadTemplates()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur enregistrement')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: number, name: string) {
    if (!window.confirm(`Supprimer le template « ${name} » ?`)) return
    setError(null)
    try {
      await deletePromptTemplate(id)
      if (editingId === id) resetForm()
      await loadTemplates()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur suppression')
    }
  }

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.templates.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{pages.templates.title}</h1>
        <p>{pages.templates.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">
            {editingId != null ? 'Modifier le template' : 'Nouveau template'}
          </h2>
        </div>
        <div className="card__body">
          <form className="form-stack" onSubmit={(e) => void handleSubmit(e)}>
            <div className="form-group">
              <label className="form-label" htmlFor="template-name">Nom</label>
              <input
                id="template-name"
                className="form-input"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="ex. Résumé technique"
                required
                maxLength={CONTENT_LIMITS.TEMPLATE_NAME_MAX}
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="template-desc">Description (optionnel)</label>
              <input
                id="template-desc"
                className="form-input"
                value={form.description}
                onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                placeholder="Courte description — pas le prompt"
                maxLength={CONTENT_LIMITS.TEMPLATE_DESCRIPTION_MAX}
              />
              <p className="form-hint">
                {formatCharCount(form.description.length, CONTENT_LIMITS.TEMPLATE_DESCRIPTION_MAX)}
              </p>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="template-content">Contenu</label>
              <textarea
                id="template-content"
                className="form-textarea"
                rows={8}
                value={form.content}
                onChange={(e) => setForm((prev) => ({ ...prev, content: e.target.value }))}
                placeholder={'Tu es un assistant…\n\n{{content}}'}
                required
                maxLength={CONTENT_LIMITS.TEMPLATE_CONTENT_MAX}
              />
              <p
                className={
                  form.content.length >= CONTENT_LIMITS.TEMPLATE_CONTENT_MAX
                    ? 'form-hint form-hint--danger'
                    : form.content.length > CONTENT_LIMITS.TEMPLATE_CONTENT_MAX * 0.8
                      ? 'form-hint form-hint--warn'
                      : 'form-hint'
                }
              >
                {formatCharCount(form.content.length, CONTENT_LIMITS.TEMPLATE_CONTENT_MAX)}
                {' · '}Utilisez {'{{content}}'} pour injecter le texte saisi dans le chat
              </p>
            </div>
            <div className="form-actions">
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? 'Enregistrement…' : editingId != null ? 'Mettre à jour' : 'Créer'}
              </button>
              {editingId != null && (
                <button type="button" className="btn btn--ghost" onClick={resetForm}>
                  Annuler
                </button>
              )}
            </div>
          </form>
        </div>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">Templates</h2>
          <span className="card__meta">{templates.length} template{templates.length !== 1 ? 's' : ''}</span>
        </div>
        <div className="card__body" style={{ padding: 0 }}>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Nom</th>
                  <th scope="col">Description</th>
                  <th scope="col">Aperçu</th>
                  <th scope="col">Créé</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {templates.map((t) => (
                  <tr key={t.id}>
                    <td><strong>{t.name}</strong></td>
                    <td title={t.description || undefined}>
                      {t.description ? truncatePreview(t.description, 60) : '—'}
                    </td>
                    <td
                      className="mono table-cell--clip"
                      title={t.content}
                    >
                      {truncatePreview(t.content, 80)}
                    </td>
                    <td className="mono">{formatDateTime(t.createdAt)}</td>
                    <td>
                      <div className="table-actions">
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          onClick={() => startEdit(t)}
                        >
                          Éditer
                        </button>
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm btn--danger"
                          onClick={() => void handleDelete(t.id, t.name)}
                        >
                          Supprimer
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && templates.length === 0 && (
                  <tr>
                    <td colSpan={5} className="table-empty">
                      Aucun template — créez-en un ci-dessus
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
