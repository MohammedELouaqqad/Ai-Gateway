import { useCallback, useEffect, useMemo, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { AUDIT_ACTIONS, fetchAuditLog, type AuditLogEntry, type AuditLogPage as AuditLogPageData } from '../api/auditLog'
import { useI18n } from '../i18n'
import { formatDateTime } from '../utils/mappers'

export function AuditLogPage() {
  const { pages, common } = useI18n()
  const t = pages.auditLog

  const [pageData, setPageData] = useState<AuditLogPageData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [filterAction, setFilterAction] = useState('')
  const [filterFrom, setFilterFrom] = useState('')
  const [filterTo, setFilterTo] = useState('')
  const [page, setPage] = useState(0)

  const hasActiveFilters = Boolean(filterAction || filterFrom || filterTo)

  const query = useMemo(
    () => ({
      page,
      size: 20,
      action: filterAction || undefined,
      from: filterFrom ? new Date(`${filterFrom}T00:00:00.000Z`).toISOString() : undefined,
      to: filterTo ? new Date(`${filterTo}T23:59:59.999Z`).toISOString() : undefined,
    }),
    [page, filterAction, filterFrom, filterTo],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setPageData(await fetchAuditLog(query))
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setLoading(false)
    }
  }, [query, common.loadError])

  useEffect(() => {
    void load()
  }, [load])

  function clearFilters() {
    setFilterAction('')
    setFilterFrom('')
    setFilterTo('')
    setPage(0)
  }

  function renderDetails(entry: AuditLogEntry): string {
    if (!entry.details || Object.keys(entry.details).length === 0) return t.noDetails
    return Object.entries(entry.details)
      .map(([field, change]) => `${field}: ${String(change.old)} → ${String(change.new)}`)
      .join(', ')
  }

  const entries = pageData?.content ?? []

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: t.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{t.title}</h1>
          <p>{t.subtitle}</p>
        </div>
      </header>

      <section className="usage-filters" aria-label={t.title}>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{t.filterAction}</span>
          <select
            className="form-select"
            value={filterAction}
            onChange={(e) => { setPage(0); setFilterAction(e.target.value) }}
          >
            <option value="">{t.filterAllActions}</option>
            {AUDIT_ACTIONS.map((action) => (
              <option key={action} value={action}>{action}</option>
            ))}
          </select>
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{t.from}</span>
          <input type="date" className="form-input" value={filterFrom} onChange={(e) => { setPage(0); setFilterFrom(e.target.value) }} />
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{t.to}</span>
          <input type="date" className="form-input" value={filterTo} onChange={(e) => { setPage(0); setFilterTo(e.target.value) }} />
        </label>
        {hasActiveFilters && (
          <button type="button" className="btn btn--ghost usage-filters__clear" onClick={clearFilters}>
            {t.reset}
          </button>
        )}
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{t.title}</h2>
          <span className="card__meta">{t.entries.replace('{n}', String(pageData?.totalElements ?? 0))}</span>
        </div>
        <div className="card__body" style={{ padding: 0 }}>
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">{t.actorCol}</th>
                  <th scope="col">{t.actionCol}</th>
                  <th scope="col">{t.targetCol}</th>
                  <th scope="col">{t.detailsCol}</th>
                  <th scope="col">{t.whenCol}</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.actorLabel}</td>
                    <td><span className="tag">{entry.action}</span></td>
                    <td>
                      {entry.targetType}
                      {entry.targetLabel ? ` — ${entry.targetLabel}` : t.noTarget}
                    </td>
                    <td style={{ fontSize: '0.85rem' }}>{renderDetails(entry)}</td>
                    <td className="mono" style={{ fontSize: '0.8rem' }}>{formatDateTime(entry.createdAt)}</td>
                  </tr>
                ))}
                {!loading && entries.length === 0 && (
                  <tr>
                    <td colSpan={5} className="table-empty">{t.empty}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          {pageData && pageData.totalPages > 1 && (
            <div className="usage-pagination">
              <button type="button" className="btn btn--ghost btn--sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                {t.prev}
              </button>
              <span className="card__meta">
                {t.page.replace('{n}', String(page + 1)).replace('{total}', String(pageData.totalPages))}
              </span>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                disabled={page + 1 >= pageData.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                {t.next}
              </button>
            </div>
          )}
        </div>
      </section>
    </AppLayout>
  )
}
