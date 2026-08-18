import { apiFetch } from './client'
import { withAuthHeaders } from '../auth/session'
import type { ApiUsageLog, UsageSummary } from './types'

export interface UsageSummaryExtended extends UsageSummary {
  errorCount?: number
  errorRate?: number | null
  avgLatencyMs?: number | null
}

export interface UsageLogsPage {
  content: ApiUsageLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UsageQuery {
  from?: string
  to?: string
  model?: string
  provider?: string
  location?: 'LOCAL' | 'CLOUD' | ''
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface UsageByModelRow {
  model: string
  provider: string
  type: 'onprem' | 'cloud'
  location?: string
  requests: number
  tokens: number
  cost: number
  sharePercent: number
}

export interface UsageByUserRow {
  user: string
  requests: number
  tokens: number
  cost: number
}

function toParams(query: UsageQuery = {}): string {
  const p = new URLSearchParams()
  if (query.from) p.set('from', query.from)
  if (query.to) p.set('to', query.to)
  if (query.model) p.set('model', query.model)
  if (query.provider) p.set('provider', query.provider)
  if (query.location) p.set('location', query.location)
  if (query.q) p.set('q', query.q)
  if (query.page != null) p.set('page', String(query.page))
  if (query.size != null) p.set('size', String(query.size))
  if (query.sort) p.set('sort', query.sort)
  const s = p.toString()
  return s ? `?${s}` : ''
}

export function fetchUsageSummary(query: UsageQuery = {}): Promise<UsageSummaryExtended> {
  return apiFetch<UsageSummaryExtended>(`/usage/summary${toParams(query)}`)
}

export function fetchUsageLogs(): Promise<ApiUsageLog[]> {
  return apiFetch<ApiUsageLog[]>('/usage')
}

export function fetchUsageLogsPage(query: UsageQuery = {}): Promise<UsageLogsPage> {
  return apiFetch<UsageLogsPage>(`/usage/logs${toParams({ size: 20, sort: 'createdAt,desc', ...query })}`)
}

export function fetchUsageByModel(query: UsageQuery = {}): Promise<UsageByModelRow[]> {
  return apiFetch<UsageByModelRow[]>(`/usage/by-model${toParams(query)}`)
}

export function fetchUsageByUser(query: UsageQuery = {}): Promise<UsageByUserRow[]> {
  return apiFetch<UsageByUserRow[]>(`/usage/by-user${toParams(query)}`)
}

/** Download CSV via authenticated fetch (blob). */
export async function downloadUsageCsv(query: UsageQuery = {}): Promise<void> {
  const response = await fetch(`/api/usage/export/csv${toParams(query)}`, {
    headers: withAuthHeaders(),
  })
  if (!response.ok) {
    throw new Error(`Export CSV failed (${response.status})`)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'usage-export.csv'
  a.click()
  URL.revokeObjectURL(url)
}
