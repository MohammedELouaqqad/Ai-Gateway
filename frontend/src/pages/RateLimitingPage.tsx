import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { ScopeBadge } from '../components/ScopeBadge'
import {
  fetchGuardrailsConfig,
  fetchGuardrailsStatus,
  updateGuardrailsConfig,
  type GuardrailsConfig,
  type GuardrailsStatus,
} from '../api/guardrails'
import { useI18n } from '../i18n'

function ProgressBar({ used, limit, warnAt = 0.8 }: { used: number; limit: number; warnAt?: number }) {
  const ratio = limit <= 0 ? 0 : Math.min(1, used / limit)
  const pct = Math.round(ratio * 100)
  const tone = ratio >= 1 ? 'error' : ratio >= warnAt ? 'warning' : 'ok'
  return (
    <div className={`guard-progress guard-progress--${tone}`}>
      <div className="guard-progress__bar" style={{ width: `${pct}%` }} />
      <span className="guard-progress__label">
        {used.toLocaleString()} / {limit.toLocaleString()} ({pct}%)
      </span>
    </div>
  )
}

export function RateLimitingPage() {
  const { pages, common, locale } = useI18n()
  const r = pages.rateLimit
  const [config, setConfig] = useState<GuardrailsConfig | null>(null)
  const [status, setStatus] = useState<GuardrailsStatus | null>(null)
  const [form, setForm] = useState<Partial<GuardrailsConfig>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [cfg, st] = await Promise.all([fetchGuardrailsConfig(), fetchGuardrailsStatus()])
      setConfig(cfg)
      setStatus(st)
      setForm({
        enabled: cfg.enabled,
        requestsPerMinute: cfg.requestsPerMinute,
        requestsPerDay: cfg.requestsPerDay,
        globalBudgetPerDayEur: cfg.globalBudgetPerDayEur,
        userBudgetPerDayEur: cfg.userBudgetPerDayEur,
      })
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : common.loadError)
    } finally {
      setLoading(false)
    }
  }, [common.loadError])

  useEffect(() => {
    void load()
  }, [load])

  async function onSave(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    try {
      const updated = await updateGuardrailsConfig(form)
      setConfig(updated)
      const st = await fetchGuardrailsStatus()
      setStatus(st)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : common.loadError)
    } finally {
      setSaving(false)
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
            <h2 className="card__title">{r.speedTitle}</h2>
            <ScopeBadge scope="session" locale={locale}>{r.speedBadge}</ScopeBadge>
          </div>
          <div className="card__body">
            <p className="muted">{r.speedDesc}</p>
            {status && (
              <>
                <div className="guard-metric">
                  <span>{r.rpm}</span>
                  <ProgressBar
                    used={status.speed.requestsPerMinute.used}
                    limit={status.speed.requestsPerMinute.limit}
                  />
                </div>
                <div className="guard-metric">
                  <span>{r.rpd}</span>
                  <ProgressBar
                    used={status.speed.requestsPerDay.used}
                    limit={status.speed.requestsPerDay.limit}
                  />
                  <span className="kpi-card__hint">
                    {r.resetAt.replace('{time}', new Date(status.speed.requestsPerDay.resetsAt).toLocaleString())}
                  </span>
                </div>
              </>
            )}
          </div>
        </section>

        <section className="section card">
          <div className="card__header">
            <h2 className="card__title">{r.amountTitle}</h2>
            <ScopeBadge scope="total" locale={locale}>{r.amountBadge}</ScopeBadge>
          </div>
          <div className="card__body">
            <p className="muted">{r.amountDesc}</p>
            {status && (
              <>
                <div className="guard-metric">
                  <span>{r.userBudget}</span>
                  <ProgressBar
                    used={Number(status.amount.userBudgetEur.used.toFixed(4))}
                    limit={status.amount.userBudgetEur.limit}
                  />
                </div>
                <div className="guard-metric">
                  <span>{r.globalBudget}</span>
                  <ProgressBar
                    used={Number(status.amount.globalBudgetEur.used.toFixed(4))}
                    limit={status.amount.globalBudgetEur.limit}
                  />
                  {status.amount.globalBudgetEur.ratio >= 0.8 && (
                    <span className="kpi-card__hint" style={{ color: 'var(--warning)' }}>
                      {r.alert80}
                    </span>
                  )}
                </div>
              </>
            )}
            <p className="kpi-card__hint">{status?.note ?? r.ollamaNote}</p>
          </div>
        </section>
      </div>

      <section className="section card">
        <div className="card__header">
          <h2 className="card__title">{r.configTitle}</h2>
        </div>
        <div className="card__body">
          <form className="guard-form" onSubmit={(e) => void onSave(e)}>
            <label className="guard-form__row">
              <span>{r.enabled}</span>
              <input
                type="checkbox"
                checked={form.enabled ?? true}
                onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.checked }))}
              />
            </label>
            <label className="guard-form__row">
              <span>{r.rpm}</span>
              <input
                type="number"
                min={1}
                value={form.requestsPerMinute ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, requestsPerMinute: Number(e.target.value) }))}
              />
            </label>
            <label className="guard-form__row">
              <span>{r.rpd}</span>
              <input
                type="number"
                min={1}
                value={form.requestsPerDay ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, requestsPerDay: Number(e.target.value) }))}
              />
            </label>
            <label className="guard-form__row">
              <span>{r.userBudget}</span>
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.userBudgetPerDayEur ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, userBudgetPerDayEur: Number(e.target.value) }))}
              />
            </label>
            <label className="guard-form__row">
              <span>{r.globalBudget}</span>
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.globalBudgetPerDayEur ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, globalBudgetPerDayEur: Number(e.target.value) }))}
              />
            </label>
            <button type="submit" className="btn btn--primary" disabled={saving || !config}>
              {saving ? common.saving : r.save}
            </button>
            <p className="kpi-card__hint">{r.runtimeNote}</p>
          </form>
        </div>
      </section>
    </AppLayout>
  )
}
