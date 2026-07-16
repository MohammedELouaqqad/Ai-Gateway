import { useEffect, useMemo, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { Badge } from '../components/Badge'
import { fetchUsageLogs, fetchUsageSummary } from '../api/usage'
import type { ApiUsageLog, UsageSummary } from '../api/types'
import { useI18n } from '../i18n'
import { providerToUiType } from '../utils/mappers'

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)} M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)} K`
  return String(n)
}

function isSuccessStatus(status: ApiUsageLog['status']): boolean {
  return status === 'SUCCESS' || status === 0
}

function dayKey(iso?: string): string | null {
  if (!iso) return null
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return null
  return d.toISOString().slice(0, 10)
}

export function UsagePage() {
  const { pages, fmt, labels, common, locale } = useI18n()
  const usage = pages.usage
  const localeTag = locale === 'en' ? 'en-US' : 'fr-FR'
  const [summary, setSummary] = useState<UsageSummary | null>(null)
  const [logs, setLogs] = useState<ApiUsageLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [filterModel, setFilterModel] = useState('')
  const [filterProvider, setFilterProvider] = useState('')
  const [filterFrom, setFilterFrom] = useState('')
  const [filterTo, setFilterTo] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([fetchUsageSummary(), fetchUsageLogs()])
      .then(([summaryData, logsData]) => {
        if (!cancelled) {
          setSummary(summaryData)
          setLogs(logsData)
        }
      })
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
  }, [common.loadError])

  const modelOptions = useMemo(() => {
    const names = new Set<string>()
    for (const log of logs) {
      const label = log.model?.displayName ?? log.model?.name
      if (label) names.add(label)
    }
    return [...names].sort((a, b) => a.localeCompare(b, localeTag))
  }, [logs, localeTag])

  const providerOptions = useMemo(() => {
    const names = new Set<string>()
    for (const log of logs) {
      const label = log.model?.provider?.name
      if (label) names.add(label)
    }
    return [...names].sort((a, b) => a.localeCompare(b, localeTag))
  }, [logs, localeTag])

  const filteredLogs = useMemo(() => {
    return logs
      .filter((log) => {
        const modelLabel = log.model?.displayName ?? log.model?.name ?? ''
        const providerLabel = log.model?.provider?.name ?? ''
        if (filterModel && modelLabel !== filterModel) return false
        if (filterProvider && providerLabel !== filterProvider) return false
        const key = dayKey(log.createdAt)
        if (filterFrom && key && key < filterFrom) return false
        if (filterTo && key && key > filterTo) return false
        if ((filterFrom || filterTo) && !key) return false
        return true
      })
      .slice()
      .sort((a, b) => {
        const ta = new Date(a.createdAt ?? 0).getTime()
        const tb = new Date(b.createdAt ?? 0).getTime()
        return tb - ta
      })
  }, [logs, filterModel, filterProvider, filterFrom, filterTo])

  const avgLatencyMs = useMemo(() => {
    const withLatency = filteredLogs.filter((l) => l.latencyMs != null)
    if (withLatency.length === 0) return null
    const sum = withLatency.reduce((s, l) => s + (l.latencyMs ?? 0), 0)
    return Math.round(sum / withLatency.length)
  }, [filteredLogs])

  const usageByModel = useMemo(() => {
    const map = new Map<
      string,
      { model: string; provider: string; type: 'onprem' | 'cloud'; requests: number; tokens: number; cost: number }
    >()
    for (const log of filteredLogs) {
      const key = `${log.model?.provider?.name ?? '—'}-${log.model?.name ?? '—'}`
      const existing = map.get(key) ?? {
        model: log.model?.displayName ?? log.model?.name ?? '—',
        provider: log.model?.provider?.name ?? '—',
        type: providerToUiType(log.model?.provider),
        requests: 0,
        tokens: 0,
        cost: 0,
      }
      existing.requests += 1
      existing.tokens += log.totalTokens ?? 0
      existing.cost += log.estimatedCost ?? 0
      map.set(key, existing)
    }
    const total = [...map.values()].reduce((s, m) => s + m.tokens, 0) || 1
    return [...map.values()]
      .map((m) => ({
        ...m,
        share: `${Math.round((m.tokens / total) * 100)} %`,
      }))
      .sort((a, b) => b.tokens - a.tokens)
  }, [filteredLogs])

  const hasActiveFilters = Boolean(filterModel || filterProvider || filterFrom || filterTo)

  function clearFilters() {
    setFilterModel('')
    setFilterProvider('')
    setFilterFrom('')
    setFilterTo('')
  }

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.usage.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header">
        <h1>{pages.usage.title}</h1>
        <p>{pages.usage.subtitle}</p>
      </header>

      <section className="kpi-grid" aria-label={pages.usage.title}>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.tokens}</span>
          </div>
          <div className="kpi-card__value">
            {summary ? formatTokens(summary.totalTokens) : '—'}
          </div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.estimatedCost}</span>
          </div>
          <div className="kpi-card__value">
            {summary ? fmt.currency(summary.totalCost) : '—'}
          </div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.requests}</span>
          </div>
          <div className="kpi-card__value">
            {summary ? fmt.num(summary.requestCount) : '—'}
          </div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.avgLatency}</span>
          </div>
          <div className="kpi-card__value">
            {avgLatencyMs != null ? `${fmt.num(avgLatencyMs)} ms` : '—'}
          </div>
          {hasActiveFilters && (
            <p className="kpi-card__hint">{usage.filterHint}</p>
          )}
        </article>
      </section>

      <section className="usage-filters" aria-label={pages.usage.title}>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{common.model}</span>
          <select
            className="form-select"
            value={filterModel}
            onChange={(e) => setFilterModel(e.target.value)}
          >
            <option value="">{common.all}</option>
            {modelOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{common.provider}</span>
          <select
            className="form-select"
            value={filterProvider}
            onChange={(e) => setFilterProvider(e.target.value)}
          >
            <option value="">{common.all}</option>
            {providerOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{usage.from}</span>
          <input
            type="date"
            className="form-input"
            value={filterFrom}
            onChange={(e) => setFilterFrom(e.target.value)}
          />
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{usage.to}</span>
          <input
            type="date"
            className="form-input"
            value={filterTo}
            onChange={(e) => setFilterTo(e.target.value)}
          />
        </label>
        {hasActiveFilters && (
          <button type="button" className="btn btn--ghost usage-filters__clear" onClick={clearFilters}>
            {usage.reset}
          </button>
        )}
      </section>

      <div className="grid-2">
        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{usage.journal}</h2>
            <span className="card__meta">
              {usage.entries.replace('{n}', String(filteredLogs.length))}
            </span>
          </div>
          <div className="card__body" style={{ padding: 0 }}>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">{common.model}</th>
                    <th scope="col">{common.tokens}</th>
                    <th scope="col">{common.cost}</th>
                    <th scope="col">{common.latency}</th>
                    <th scope="col">{common.status}</th>
                    <th scope="col">{common.date}</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredLogs.map((log) => {
                    const ok = isSuccessStatus(log.status)
                    return (
                      <tr key={log.id}>
                        <td>
                          <span className="tag tag--model">
                            {log.model?.displayName ?? log.model?.name ?? '—'}
                          </span>
                        </td>
                        <td className="mono">{formatTokens(log.totalTokens)}</td>
                        <td className="mono">{fmt.currency(log.estimatedCost)}</td>
                        <td className="mono">{log.latencyMs != null ? `${log.latencyMs} ms` : '—'}</td>
                        <td>
                          <Badge variant={ok ? 'success' : 'error'} dot>
                            {ok ? 'OK' : 'ERR'}
                          </Badge>
                        </td>
                        <td className="mono" style={{ fontSize: '0.8rem' }}>
                          {log.createdAt
                            ? new Date(log.createdAt).toLocaleString(locale === 'en' ? 'en-US' : 'fr-FR')
                            : '—'}
                        </td>
                      </tr>
                    )
                  })}
                  {!loading && filteredLogs.length === 0 && (
                    <tr>
                      <td colSpan={6} className="table-empty">
                        {logs.length === 0 ? usage.noLogs : usage.noFilterResults}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{usage.byModel}</h2>
          </div>
          <div className="card__body" style={{ padding: 0 }}>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">{common.model}</th>
                    <th scope="col">{common.provider}</th>
                    <th scope="col">{common.type}</th>
                    <th scope="col">{usage.req}</th>
                    <th scope="col">{common.tokens}</th>
                    <th scope="col">{common.cost}</th>
                    <th scope="col">{usage.share}</th>
                  </tr>
                </thead>
                <tbody>
                  {usageByModel.map((m) => (
                    <tr key={`${m.provider}-${m.model}`}>
                      <td><span className="tag tag--model">{m.model}</span></td>
                      <td>{m.provider}</td>
                      <td>
                        <span className={`tag ${m.type === 'onprem' ? 'tag--onprem' : 'tag--cloud'}`}>
                          {m.type === 'onprem' ? 'LOCAL' : 'CLOUD'}
                        </span>
                      </td>
                      <td className="mono">{fmt.num(m.requests)}</td>
                      <td className="mono">{formatTokens(m.tokens)}</td>
                      <td className="mono">{fmt.currency(m.cost)}</td>
                      <td className="mono">{m.share}</td>
                    </tr>
                  ))}
                  {!loading && usageByModel.length === 0 && (
                    <tr>
                      <td colSpan={7} className="table-empty">{usage.noAggregate}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </div>
    </AppLayout>
  )
}
