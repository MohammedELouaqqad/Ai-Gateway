import { apiFetch } from './client'
import type { ApiUsageLog, UsageSummary } from './types'

export function fetchUsageSummary(): Promise<UsageSummary> {
  return apiFetch<UsageSummary>('/usage/summary')
}

export function fetchUsageLogs(): Promise<ApiUsageLog[]> {
  return apiFetch<ApiUsageLog[]>('/usage')
}
