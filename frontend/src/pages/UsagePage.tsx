import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { Badge } from '../components/Badge'
import { ScopeBadge } from '../components/ScopeBadge'
import { TrendChart } from '../components/TrendChart'
import {
  downloadUsageCsv,
  fetchUsageByModel,
  fetchUsageLogsPage,
  fetchUsageSummary,
  type UsageByModelRow,
  type UsageLogsPage,
  type UsageSummaryExtended,
} from '../api/usage'
import type { ApiUsageLog } from '../api/types'
import { useI18n } from '../i18n'
import { formatTokens } from '../utils/formatTokens'
import {
  dailyTrends,
  formatMs,
  isSuccessStatus,
  latencyByKind,
  latencyPercentiles,
} from '../utils/usageStats'

type LocationFilter = '' | 'LOCAL' | 'CLOUD'

export function UsagePage() {
  const { pages, fmt, labels, common, locale } = useI18n()
  const usage = pages.usage
  const localeTag = locale === 'en' ? 'en-US' : 'fr-FR'

  const [summary, setSummary] = useState<UsageSummaryExtended | null>(null)
  const [pageData, setPageData] = useState<UsageLogsPage | null>(null)
  const [byModel, setByModel] = useState<UsageByModelRow[]>([])
  const [trendLogs, setTrendLogs] = useState<ApiUsageLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)

  // Deep-linkable from other pages (e.g. Models' "View usage" when delete is blocked) — the URL
  // param seeds the initial filter, then behaves like any other in-page filter afterward (no
  // further URL sync — reading it once on mount is enough for this entry-point use case).
  const [searchParams] = useSearchParams()
  const [filterModel, setFilterModel] = useState(() => searchParams.get('model') ?? '')
  const [filterProvider, setFilterProvider] = useState('')
  const [filterLocation, setFilterLocation] = useState<LocationFilter>('')
  const [filterFrom, setFilterFrom] = useState('')
  const [filterTo, setFilterTo] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const hasActiveFilters = Boolean(
    filterModel || filterProvider || filterLocation || filterFrom || filterTo || search,
  )
  const kpiScope = hasActiveFilters ? 'range' : 'total'

  const query = useMemo(() => {
    const from = filterFrom ? new Date(`${filterFrom}T00:00:00.000Z`).toISOString() : undefined
    const to = filterTo ? new Date(`${filterTo}T23:59:59.999Z`).toISOString() : undefined
    return {
      from,
      to,
      model: filterModel || undefined,
      provider: filterProvider || undefined,
      location: filterLocation || undefined,
      q: search.trim() || undefined,
      page,
      size: 20,
      sort: 'createdAt,desc',
    }
  }, [filterFrom, filterTo, filterModel, filterProvider, filterLocation, search, page])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [sum, logsPage, models, allForTrends] = await Promise.all([
        fetchUsageSummary({ from: query.from, to: query.to }),
        fetchUsageLogsPage(query),
        fetchUsageByModel({
          from: query.from,
          to: query.to,
          model: query.model,
          provider: query.provider,
          location: query.location,
        }),
        // trends: reuse first page is weak — fetch summary range logs via /usage list filtered client for trends only when small
        fetchUsageLogsPage({ ...query, page: 0, size: 100 }),
      ])
      setSummary(sum)
      setPageData(logsPage)
      setByModel(models)
      setTrendLogs(allForTrends.content)
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setLoading(false)
    }
  }, [query, common.loadError])

  useEffect(() => {
    void load()
  }, [load])

  const modelOptions = useMemo(() => {
    const names = new Set<string>()
    for (const row of byModel) names.add(row.model)
    return [...names].sort((a, b) => a.localeCompare(b, localeTag))
  }, [byModel, localeTag])

  const providerOptions = useMemo(() => {
    const names = new Set<string>()
    for (const row of byModel) names.add(row.provider)
    return [...names].sort((a, b) => a.localeCompare(b, localeTag))
  }, [byModel, localeTag])

  const percentiles = useMemo(() => latencyPercentiles(trendLogs), [trendLogs])
  const byKind = useMemo(() => latencyByKind(trendLogs), [trendLogs])
  const trends = useMemo(() => dailyTrends(trendLogs), [trendLogs])

  function clearFilters() {
    setFilterModel('')
    setFilterProvider('')
    setFilterLocation('')
    setFilterFrom('')
    setFilterTo('')
    setSearch('')
    setPage(0)
  }

  async function onExport() {
    setExporting(true)
    try {
      await downloadUsageCsv({
        from: query.from,
        to: query.to,
        model: query.model,
        provider: query.provider,
        location: query.location,
        q: query.q,
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setExporting(false)
    }
  }

  const logs = pageData?.content ?? []

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.usage.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{pages.usage.title}</h1>
          <p>{pages.usage.subtitle}</p>
          <ScopeBadge scope={kpiScope} locale={locale} />
        </div>
        <div className="page-header__actions">
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void onExport()} disabled={exporting}>
            {usage.exportCsv}
          </button>
        </div>
      </header>

      <section className="kpi-grid" aria-label={pages.usage.title}>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.tokens}</span>
            <ScopeBadge scope={kpiScope} locale={locale} />
          </div>
          <div className="kpi-card__value">{summary ? formatTokens(summary.totalTokens) : '—'}</div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.estimatedCost}</span>
            <ScopeBadge scope={kpiScope} locale={locale} />
          </div>
          <div className="kpi-card__value">{summary ? fmt.currency(summary.totalCost) : '—'}</div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">{labels.requests}</span>
            <ScopeBadge scope={kpiScope} locale={locale} />
          </div>
          <div className="kpi-card__value">{summary ? fmt.num(summary.requestCount) : '—'}</div>
        </article>
        <article className="kpi-card">
          <div className="kpi-card__header">
            <span className="kpi-card__label">
              {usage.latencyP50} / {usage.latencyP95} / {usage.latencyP99}
            </span>
            <ScopeBadge scope={kpiScope} locale={locale} />
          </div>
          <div className="kpi-card__value" style={{ fontSize: '1rem' }}>
            {formatMs(percentiles.p50, localeTag)} / {formatMs(percentiles.p95, localeTag)} /{' '}
            {formatMs(percentiles.p99, localeTag)}
          </div>
          <p className="kpi-card__hint">
            {usage.latencyLocal}: {formatMs(byKind.local.p95, localeTag)} p95 · {usage.latencyCloud}:{' '}
            {formatMs(byKind.cloud.p95, localeTag)} p95
          </p>
        </article>
      </section>

      <section className="usage-filters" aria-label={pages.usage.title}>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{common.model}</span>
          <select className="form-select" value={filterModel} onChange={(e) => { setPage(0); setFilterModel(e.target.value) }}>
            <option value="">{common.all}</option>
            {modelOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{common.provider}</span>
          <select className="form-select" value={filterProvider} onChange={(e) => { setPage(0); setFilterProvider(e.target.value) }}>
            <option value="">{common.all}</option>
            {providerOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{common.type}</span>
          <select
            className="form-select"
            value={filterLocation}
            onChange={(e) => { setPage(0); setFilterLocation(e.target.value as LocationFilter) }}
          >
            <option value="">{usage.locationAll}</option>
            <option value="LOCAL">{usage.locationLocal}</option>
            <option value="CLOUD">{usage.locationCloud}</option>
          </select>
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{usage.from}</span>
          <input type="date" className="form-input" value={filterFrom} onChange={(e) => { setPage(0); setFilterFrom(e.target.value) }} />
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{usage.to}</span>
          <input type="date" className="form-input" value={filterTo} onChange={(e) => { setPage(0); setFilterTo(e.target.value) }} />
        </label>
        <label className="usage-filters__field">
          <span className="usage-filters__label">{usage.search}</span>
          <input
            className="form-input"
            value={search}
            placeholder={usage.search}
            onChange={(e) => { setPage(0); setSearch(e.target.value) }}
          />
        </label>
        {hasActiveFilters && (
          <button type="button" className="btn btn--ghost usage-filters__clear" onClick={clearFilters}>
            {usage.reset}
          </button>
        )}
      </section>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{usage.trends}</h2>
          <ScopeBadge scope={kpiScope} locale={locale} />
        </div>
        <div className="card__body">
          <div className="usage-trends-grid">
            <div>
              <h3 className="obs-mini-title">{usage.trendCost}</h3>
              <TrendChart
                days={trends.map((t) => t.day)}
                emptyLabel={usage.noTrend}
                formatY={(n) => fmt.currency(n)}
                series={[{ key: 'cost', label: usage.trendCost, color: 'var(--accent, #3b82f6)', values: trends.map((t) => t.cost) }]}
              />
            </div>
            <div>
              <h3 className="obs-mini-title">{usage.trendRequests}</h3>
              <TrendChart
                days={trends.map((t) => t.day)}
                emptyLabel={usage.noTrend}
                formatY={(n) => fmt.num(Math.round(n))}
                series={[{ key: 'req', label: usage.trendRequests, color: 'var(--success, #22c55e)', values: trends.map((t) => t.requests) }]}
              />
            </div>
            <div>
              <h3 className="obs-mini-title">{usage.trendTokens}</h3>
              <TrendChart
                days={trends.map((t) => t.day)}
                emptyLabel={usage.noTrend}
                formatY={(n) => formatTokens(n)}
                series={[{ key: 'tok', label: usage.trendTokens, color: 'var(--warning, #f59e0b)', values: trends.map((t) => t.tokens) }]}
              />
            </div>
          </div>
        </div>
      </section>

      <div className="grid-2">
        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{usage.journal}</h2>
            <span className="card__meta">
              {usage.entries.replace('{n}', String(pageData?.totalElements ?? 0))}
            </span>
          </div>
          <div className="card__body" style={{ padding: 0 }}>
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">{common.model}</th>
                    <th scope="col">{common.provider}</th>
                    <th scope="col">{common.tokens}</th>
                    <th scope="col">{common.cost}</th>
                    <th scope="col">{common.latency}</th>
                    <th scope="col">{common.status}</th>
                    <th scope="col">{common.date}</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => {
                    const ok = isSuccessStatus(log.status)
                    const loc = log.model?.provider?.location === 'CLOUD' ? 'CLOUD' : 'LOCAL'
                    return (
                      <tr key={log.id}>
                        <td>
                          <span className="tag tag--model">
                            {log.model?.displayName ?? log.model?.name ?? '—'}
                          </span>
                        </td>
                        <td>
                          <span className={`tag ${loc === 'LOCAL' ? 'tag--onprem' : 'tag--cloud'}`}>{loc}</span>
                          {' '}
                          {log.model?.provider?.name ?? '—'}
                        </td>
                        <td className="mono">{formatTokens(log.totalTokens)}</td>
                        <td className="mono">{fmt.currency(log.estimatedCost)}</td>
                        <td className="mono">{formatMs(log.latencyMs, localeTag)}</td>
                        <td>
                          <Badge variant={ok ? 'success' : 'error'} dot>{ok ? 'OK' : 'ERR'}</Badge>
                        </td>
                        <td className="mono" style={{ fontSize: '0.8rem' }}>
                          {log.createdAt ? new Date(log.createdAt).toLocaleString(localeTag) : '—'}
                        </td>
                      </tr>
                    )
                  })}
                  {!loading && logs.length === 0 && (
                    <tr>
                      <td colSpan={7} className="table-empty">
                        {usage.noFilterResults}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            {pageData && pageData.totalPages > 1 && (
              <div className="usage-pagination">
                <button type="button" className="btn btn--ghost btn--sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                  {usage.prev}
                </button>
                <span className="card__meta">
                  {usage.page.replace('{n}', String(page + 1)).replace('{total}', String(pageData.totalPages))}
                </span>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  disabled={page + 1 >= pageData.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  {usage.next}
                </button>
              </div>
            )}
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
                  {byModel.map((m) => (
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
                      <td className="mono">{m.sharePercent} %</td>
                    </tr>
                  ))}
                  {!loading && byModel.length === 0 && (
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
