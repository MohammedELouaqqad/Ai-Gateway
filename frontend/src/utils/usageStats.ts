import type { ApiUsageLog } from '../api/types'
import { providerToUiType } from './mappers'

export function isSuccessStatus(status: ApiUsageLog['status']): boolean {
  return status === 'SUCCESS' || status === 0 || status === '0'
}

export function dayKey(iso?: string): string | null {
  if (!iso) return null
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return null
  return d.toISOString().slice(0, 10)
}

/** Percentile of sorted numeric sample (linear interpolation). */
export function percentile(sortedAsc: number[], p: number): number | null {
  if (sortedAsc.length === 0) return null
  if (sortedAsc.length === 1) return sortedAsc[0]
  const rank = (p / 100) * (sortedAsc.length - 1)
  const lo = Math.floor(rank)
  const hi = Math.ceil(rank)
  if (lo === hi) return sortedAsc[lo]
  const w = rank - lo
  return sortedAsc[lo] * (1 - w) + sortedAsc[hi] * w
}

export function latenciesMs(logs: ApiUsageLog[]): number[] {
  return logs
    .map((l) => l.latencyMs)
    .filter((v): v is number => v != null && Number.isFinite(v))
    .sort((a, b) => a - b)
}

export function latencyPercentiles(logs: ApiUsageLog[]): {
  p50: number | null
  p95: number | null
  p99: number | null
} {
  const sorted = latenciesMs(logs)
  const p50 = percentile(sorted, 50)
  const p95 = percentile(sorted, 95)
  const p99 = percentile(sorted, 99)
  return {
    p50: p50 != null ? Math.round(p50) : null,
    p95: p95 != null ? Math.round(p95) : null,
    p99: p99 != null ? Math.round(p99) : null,
  }
}

export function successErrorRate(logs: ApiUsageLog[]): {
  total: number
  success: number
  error: number
  errorRate: number | null
} {
  const total = logs.length
  if (total === 0) {
    return { total: 0, success: 0, error: 0, errorRate: null }
  }
  const success = logs.filter((l) => isSuccessStatus(l.status)).length
  const error = total - success
  return {
    total,
    success,
    error,
    errorRate: error / total,
  }
}

export type ProviderKind = 'local' | 'cloud'

export function logProviderKind(log: ApiUsageLog): ProviderKind {
  const provider = log.model?.provider ?? log.provider
  return providerToUiType(provider) === 'onprem' ? 'local' : 'cloud'
}

export function latencyByKind(logs: ApiUsageLog[]): Record<
  ProviderKind,
  { p50: number | null; p95: number | null; count: number }
> {
  const local = logs.filter((l) => logProviderKind(l) === 'local')
  const cloud = logs.filter((l) => logProviderKind(l) === 'cloud')
  return {
    local: { ...latencyPercentiles(local), count: local.length },
    cloud: { ...latencyPercentiles(cloud), count: cloud.length },
  }
}

export interface DailyTrendPoint {
  day: string
  cost: number
  requests: number
  tokens: number
  errors: number
}

export function dailyTrends(logs: ApiUsageLog[]): DailyTrendPoint[] {
  const map = new Map<string, DailyTrendPoint>()
  for (const log of logs) {
    const day = dayKey(log.createdAt)
    if (!day) continue
    const row = map.get(day) ?? { day, cost: 0, requests: 0, tokens: 0, errors: 0 }
    row.requests += 1
    row.cost += log.estimatedCost ?? 0
    row.tokens += log.totalTokens ?? 0
    if (!isSuccessStatus(log.status)) row.errors += 1
    map.set(day, row)
  }
  return [...map.values()].sort((a, b) => a.day.localeCompare(b.day))
}

export function sumUsage(logs: ApiUsageLog[]): {
  tokens: number
  cost: number
  requests: number
} {
  return logs.reduce(
    (acc, l) => {
      acc.tokens += l.totalTokens ?? 0
      acc.cost += l.estimatedCost ?? 0
      acc.requests += 1
      return acc
    },
    { tokens: 0, cost: 0, requests: 0 },
  )
}

export function formatMs(ms: number | null | undefined, localeTag: string): string {
  if (ms == null) return '—'
  if (ms >= 10_000) return `${(ms / 1000).toLocaleString(localeTag, { maximumFractionDigits: 1 })} s`
  return `${ms.toLocaleString(localeTag)} ms`
}

export function formatPercent(rate: number | null | undefined, localeTag: string): string {
  if (rate == null) return '—'
  return `${(rate * 100).toLocaleString(localeTag, { maximumFractionDigits: 1 })} %`
}
