import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { Badge } from '../components/Badge'
import {
  fetchModelQuality,
  fetchRoutingConfig,
  fetchShadowStats,
  runRoutingJudge,
  simulateRouting,
  updateRoutingConfig,
  type ModelQualityResponse,
  type RoutingConfig,
  type RoutingDecision,
  type RoutingMode,
  type RoutingProfile,
  type ShadowStats,
} from '../api/routing'
import { useI18n } from '../i18n'

export function RoutingPage() {
  const { pages, common } = useI18n()
  const r = pages.routing
  const [config, setConfig] = useState<RoutingConfig | null>(null)
  const [stats, setStats] = useState<ShadowStats | null>(null)
  const [modelQuality, setModelQuality] = useState<ModelQualityResponse | null>(null)
  const [decision, setDecision] = useState<RoutingDecision | null>(null)
  const [form, setForm] = useState<Partial<RoutingConfig>>({})
  const [prompt, setPrompt] = useState('Summarize this text in 5 bullets.')
  const [statsDays, setStatsDays] = useState(7)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [simulating, setSimulating] = useState(false)
  const [judging, setJudging] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [cfg, st, quality] = await Promise.all([
        fetchRoutingConfig(),
        fetchShadowStats(statsDays),
        fetchModelQuality(),
      ])
      setConfig(cfg)
      setStats(st)
      setModelQuality(quality)
      setForm({
        enabled: cfg.enabled,
        mode: cfg.mode,
        canaryPercent: cfg.canaryPercent ?? 10,
        profile: cfg.profile,
        maxRetries: cfg.maxRetries,
        retryBackoffMs: cfg.retryBackoffMs,
      })
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setLoading(false)
    }
  }, [common.loadError, statsDays])

  useEffect(() => {
    void load()
  }, [load])

  async function onSave(e: FormEvent) {
    e.preventDefault()
    if (form.mode === 'ACTIVE' && config?.mode !== 'ACTIVE') {
      const ok = window.confirm(r.activateWarning)
      if (!ok) return
    }
    if (form.mode === 'CANARY' && config?.mode !== 'CANARY') {
      const ok = window.confirm(r.activateCanaryWarning)
      if (!ok) return
    }
    setSaving(true)
    try {
      const updated = await updateRoutingConfig(form)
      setConfig(updated)
      setForm({
        enabled: updated.enabled,
        mode: updated.mode,
        canaryPercent: updated.canaryPercent ?? 10,
        profile: updated.profile,
        maxRetries: updated.maxRetries,
        retryBackoffMs: updated.retryBackoffMs,
      })
      const st = await fetchShadowStats(statsDays)
      setStats(st)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : common.loadError)
    } finally {
      setSaving(false)
    }
  }

  async function onSimulate(e: FormEvent) {
    e.preventDefault()
    setSimulating(true)
    try {
      const result = await simulateRouting(prompt)
      setDecision(result)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : common.loadError)
    } finally {
      setSimulating(false)
    }
  }

  async function onJudgeRun() {
    setJudging(true)
    try {
      await runRoutingJudge(5, 'ALL')
      const st = await fetchShadowStats(statsDays)
      setStats(st)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : common.loadError)
    } finally {
      setJudging(false)
    }
  }

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: r.title }]}>
      <ApiStateBanner loading={loading} error={error} />

      <header className="page-header page-header--row">
        <div>
          <h1>{r.title}</h1>
          <p>{r.subtitle}</p>
        </div>
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => void load()} disabled={loading}>
          {r.refresh}
        </button>
      </header>

      <div className="guard-axes">
        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{r.observationTitle}</h2>
            <Badge variant={config?.mode === 'ACTIVE' ? 'success' : config?.mode === 'CANARY' ? 'info' : 'warning'}>
              {config?.mode ?? '—'}
            </Badge>
          </div>
          <div className="card__body">
            <p className="muted">{r.shadowPanelDesc}</p>
            <p className="muted">{r.divergenceExpected}</p>
            <p className="muted">{r.qualityHumanLoop}</p>
            <p className="muted">{r.economyLocalNote}</p>
            <div className="form-row" style={{ marginBottom: '0.75rem' }}>
              <label className="form-label" htmlFor="stats-days">{r.statsPeriod}</label>
              <select
                id="stats-days"
                className="form-select"
                value={statsDays}
                onChange={(e) => setStatsDays(Number(e.target.value))}
              >
                <option value={1}>1d</option>
                <option value={7}>7d</option>
                <option value={30}>30d</option>
              </select>
            </div>
            {stats && (
              <dl className="settings-auth__dl">
                <div>
                  <dt>{r.totalDecisions}</dt>
                  <dd className="mono">{stats.totalDecisions}</dd>
                </div>
                <div>
                  <dt>{r.divergencePct}</dt>
                  <dd className="mono">{stats.divergencePct}%</dd>
                </div>
                {typeof stats.judgeReadyDisagreements === 'number' && (
                  <div>
                    <dt>{r.judgeReady}</dt>
                    <dd className="mono">{stats.judgeReadyDisagreements}</dd>
                  </div>
                )}
                {typeof stats.canaryLive === 'number' && (
                  <div>
                    <dt>{r.canaryLive}</dt>
                    <dd className="mono">{stats.canaryLive}</dd>
                  </div>
                )}
                {typeof stats.canaryHoldout === 'number' && (
                  <div>
                    <dt>{r.canaryHoldout}</dt>
                    <dd className="mono">{stats.canaryHoldout}</dd>
                  </div>
                )}
                <div>
                  <dt>{r.appliedCost}</dt>
                  <dd className="mono">{stats.appliedCostEur.toFixed(4)} €</dd>
                </div>
                <div>
                  <dt>{r.selectedCost}</dt>
                  <dd className="mono">{stats.selectedCostEur.toFixed(4)} €</dd>
                </div>
                <div>
                  <dt>{r.potentialSaving}</dt>
                  <dd className="mono">{stats.potentialSavingEur.toFixed(4)} €</dd>
                </div>
                {typeof stats.realizedSavingEur === 'number' && (
                  <div>
                    <dt>{r.realizedSaving}</dt>
                    <dd className="mono">{stats.realizedSavingEur.toFixed(4)} €</dd>
                  </div>
                )}
                {typeof stats.judgedCount === 'number' && (
                  <div>
                    <dt>{r.judged}</dt>
                    <dd className="mono">{stats.judgedCount}</dd>
                  </div>
                )}
                {typeof stats.judgeProposedWins === 'number' && (
                  <div>
                    <dt>{r.judgeProposedWins}</dt>
                    <dd className="mono">{stats.judgeProposedWins}</dd>
                  </div>
                )}
                {typeof stats.judgeAppliedWins === 'number' && (
                  <div>
                    <dt>{r.judgeAppliedWins}</dt>
                    <dd className="mono">{stats.judgeAppliedWins}</dd>
                  </div>
                )}
                {typeof stats.judgeTies === 'number' && (
                  <div>
                    <dt>{r.judgeTies}</dt>
                    <dd className="mono">{stats.judgeTies}</dd>
                  </div>
                )}
              </dl>
            )}
            <p className="muted" style={{ marginTop: '0.75rem' }}>{r.judgeDesc}</p>
            <p className="muted">{r.realizedSavingHint}</p>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => void onJudgeRun()}
              disabled={judging || loading}
              style={{ marginTop: '0.5rem' }}
            >
              {judging ? r.judging : r.judgeRun}
            </button>
            {stats && Object.keys(stats.selectedModelDistribution).length > 0 && (
              <div style={{ marginTop: '0.75rem' }}>
                <h3 className="card__subtitle">{r.distribution}</h3>
                <ul className="muted">
                  {Object.entries(stats.selectedModelDistribution).map(([name, count]) => (
                    <li key={name}>
                      <span className="mono">{name}</span> — {count}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </section>

        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{r.qualityTitle}</h2>
            <Badge variant={modelQuality?.overallQualityStatus === 'MEASURED' ? 'info' : 'warning'}>
              {modelQuality?.overallQualityStatus === 'MEASURED'
                ? r.qualityMeasured
                : r.qualityNotMeasured}
            </Badge>
          </div>
          <div className="card__body">
            <p className="muted">{r.qualityDesc}</p>
            {modelQuality && modelQuality.models.length === 0 && (
              <p className="muted">{r.qualityEmpty}</p>
            )}
            {modelQuality && modelQuality.models.length > 0 && (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>{r.colModel}</th>
                      <th>{r.qualityUps}</th>
                      <th>{r.qualityDowns}</th>
                      <th>{r.qualityVolume}</th>
                      <th>{r.qualityUpRatio}</th>
                      <th>{r.qualityOverall}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {modelQuality.models.map((row) => (
                      <tr key={row.modelName}>
                        <td className="mono">{row.modelName}</td>
                        <td className="mono">{row.ups}</td>
                        <td className="mono">{row.downs}</td>
                        <td className="mono">{row.volume}</td>
                        <td className="mono">
                          {row.upRatio == null ? '—' : `${(row.upRatio * 100).toFixed(0)}%`}
                        </td>
                        <td>
                          {row.qualityStatus === 'MEASURED' ? r.qualityMeasured : r.qualityNotMeasured}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>

        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{r.configTitle}</h2>
          </div>
          <div className="card__body">
            <form className="guard-form" onSubmit={onSave}>
              <label className="guard-form__row">
                <span>{r.enabled}</span>
                <input
                  type="checkbox"
                  checked={Boolean(form.enabled)}
                  onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
                />
              </label>
              <label className="guard-form__row">
                <span>{r.mode}</span>
                <select
                  className="form-select"
                  value={form.mode ?? 'SHADOW'}
                  onChange={(e) => setForm((f) => ({ ...f, mode: e.target.value as RoutingMode }))}
                >
                  <option value="SHADOW">SHADOW</option>
                  <option value="CANARY">CANARY</option>
                  <option value="ACTIVE">ACTIVE</option>
                </select>
              </label>
              {form.mode === 'ACTIVE' && <p className="muted">{r.activateWarning}</p>}
              {form.mode === 'CANARY' && (
                <>
                  <p className="muted">{r.canaryHint}</p>
                  <label className="guard-form__row">
                    <span>{r.canaryPercent}</span>
                    <input
                      type="number"
                      min={0}
                      max={100}
                      className="form-input"
                      value={form.canaryPercent ?? 10}
                      onChange={(e) =>
                        setForm((f) => ({ ...f, canaryPercent: Number(e.target.value) }))
                      }
                    />
                  </label>
                </>
              )}
              <label className="guard-form__row">
                <span>{r.profile}</span>
                <select
                  className="form-select"
                  value={form.profile ?? 'BALANCED'}
                  onChange={(e) => setForm((f) => ({ ...f, profile: e.target.value as RoutingProfile }))}
                >
                  <option value="ECONOMY">ECONOMY</option>
                  <option value="QUALITY">QUALITY</option>
                  <option value="SPEED">SPEED</option>
                  <option value="BALANCED">BALANCED</option>
                </select>
              </label>
              <label className="guard-form__row">
                <span>{r.maxRetries}</span>
                <input
                  type="number"
                  min={0}
                  max={3}
                  className="form-input"
                  value={form.maxRetries ?? 1}
                  onChange={(e) => setForm((f) => ({ ...f, maxRetries: Number(e.target.value) }))}
                />
              </label>
              <label className="guard-form__row">
                <span>{r.retryBackoffMs}</span>
                <input
                  type="number"
                  min={0}
                  className="form-input"
                  value={form.retryBackoffMs ?? 200}
                  onChange={(e) => setForm((f) => ({ ...f, retryBackoffMs: Number(e.target.value) }))}
                />
              </label>
              <p className="kpi-card__hint">{r.runtimeNote}</p>
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? common.saving : r.save}
              </button>
            </form>
          </div>
        </section>
      </div>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{r.simulateTitle}</h2>
        </div>
        <div className="card__body">
          <p className="muted">{r.simulateDesc}</p>
          <form onSubmit={onSimulate}>
            <textarea
              className="form-input"
              rows={4}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder={r.simulatePlaceholder}
            />
            <button type="submit" className="btn btn--primary" style={{ marginTop: '0.75rem' }} disabled={simulating}>
              {simulating ? r.simulating : r.simulate}
            </button>
          </form>

          {decision && (
            <div style={{ marginTop: '1.25rem' }}>
              <p>
                <strong>{r.winner}:</strong>{' '}
                <span className="mono">{decision.selectedModelName ?? '—'}</span>
                {' · '}
                {r.profile}: <span className="mono">{decision.profile}</span>
              </p>

              <h3 className="card__subtitle">{r.eliminated}</h3>
              {decision.eliminated.length === 0 ? (
                <p className="muted">{r.noneEliminated}</p>
              ) : (
                <ul>
                  {decision.eliminated.map((e) => (
                    <li key={`${e.modelId}-${e.reason}`}>
                      <span className="mono">{e.modelName}</span> — {e.reason}
                    </li>
                  ))}
                </ul>
              )}

              <h3 className="card__subtitle">{r.ranked}</h3>
              {decision.ranked.length === 0 ? (
                <p className="muted">{r.noneRanked}</p>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>{r.colModel}</th>
                        <th>{r.colCost}</th>
                        <th>{r.colCostScore}</th>
                        <th>{r.colQuality}</th>
                        <th>{r.colSpeed}</th>
                        <th>{r.colTotal}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {decision.ranked.map((c, idx) => (
                        <tr key={c.modelId} className={idx === 0 ? 'table-row--highlight' : undefined}>
                          <td className="mono">{c.modelName}</td>
                          <td>{c.estimatedCostEur.toFixed(4)} €</td>
                          <td>{c.costScore}</td>
                          <td>{c.qualityScore}</td>
                          <td>{c.speedScore}</td>
                          <td>
                            <strong>{c.totalScore}</strong>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </section>
    </AppLayout>
  )
}
