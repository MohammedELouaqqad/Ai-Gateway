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
import { useI18n } from '../i18n'
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
  const { pages, common } = useI18n()
  const t = pages.templates
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
          setError(e instanceof Error ? e.message : common.loadError)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [loadTemplates, common.loadError])

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
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: number, name: string) {
    if (!window.confirm(t.deleteConfirm.replace('{name}', name))) return
    setError(null)
    try {
      await deletePromptTemplate(id)
      if (editingId === id) resetForm()
      await loadTemplates()
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    }
  }

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: t.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{t.title}</h1>
        <p>{t.subtitle}</p>
      </header>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">
            {editingId != null ? t.editTemplate : t.newTemplate}
          </h2>
        </div>
        <div className="card__body">
          <form className="form-stack" onSubmit={(e) => void handleSubmit(e)}>
            <div className="form-group">
              <label className="form-label" htmlFor="template-name">{common.name}</label>
              <input
                id="template-name"
                className="form-input"
                value={form.name}
                onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                placeholder={t.namePlaceholder}
                required
                maxLength={CONTENT_LIMITS.TEMPLATE_NAME_MAX}
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="template-desc">{t.descriptionOptional}</label>
              <input
                id="template-desc"
                className="form-input"
                value={form.description}
                onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                placeholder={t.descriptionPlaceholder}
                maxLength={CONTENT_LIMITS.TEMPLATE_DESCRIPTION_MAX}
              />
              <p className="form-hint">
                {formatCharCount(form.description.length, CONTENT_LIMITS.TEMPLATE_DESCRIPTION_MAX)}
              </p>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="template-content">{t.content}</label>
              <textarea
                id="template-content"
                className="form-textarea"
                rows={8}
                value={form.content}
                onChange={(e) => setForm((prev) => ({ ...prev, content: e.target.value }))}
                placeholder={t.contentPlaceholder}
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
                {' · '}{t.contentHint}
              </p>
          </div>
            <div className="form-actions">
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? common.saving : editingId != null ? common.update : common.create}
              </button>
              {editingId != null && (
                <button type="button" className="btn btn--ghost" onClick={resetForm}>
                  {common.cancel}
                </button>
              )}
          </div>
          </form>
        </div>
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{t.listTitle}</h2>
          <span className="card__meta">
            {t.count.replace('{n}', String(templates.length))}
          </span>
        </div>
        <div className="card__body" style={{ padding: 0 }}>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{common.name}</th>
                  <th scope="col">{common.description}</th>
                  <th scope="col">{common.preview}</th>
                  <th scope="col">{common.created}</th>
                  <th scope="col">{common.actions}</th>
                </tr>
              </thead>
              <tbody>
                {templates.map((tpl) => (
                  <tr key={tpl.id}>
                    <td><strong>{tpl.name}</strong></td>
                    <td title={tpl.description || undefined}>
                      {tpl.description ? truncatePreview(tpl.description, 60) : '—'}
                    </td>
                    <td
                      className="mono table-cell--clip"
                      title={tpl.content}
                    >
                      {truncatePreview(tpl.content, 80)}
                    </td>
                    <td className="mono">{formatDateTime(tpl.createdAt)}</td>
                    <td>
                      <div className="table-actions">
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          onClick={() => startEdit(tpl)}
                        >
                          {common.edit}
                        </button>
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm btn--danger"
                          onClick={() => void handleDelete(tpl.id, tpl.name)}
                        >
                          {common.delete}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && templates.length === 0 && (
                  <tr>
                    <td colSpan={5} className="table-empty">
                      {t.empty}
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
