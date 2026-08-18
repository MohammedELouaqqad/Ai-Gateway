import { apiFetch } from './client'

export interface GuardrailsConfig {
  enabled: boolean
  requestsPerMinute: number
  requestsPerDay: number
  globalBudgetPerDayEur: number
  userBudgetPerDayEur: number
  assumedCompletionTokens?: number
  pricing?: Record<string, { inputPer1kEur: number; outputPer1kEur: number }>
}

export interface GuardrailsStatus {
  enabled: boolean
  speed: {
    requestsPerMinute: { limit: number; used: number; resetsAtHint?: string }
    requestsPerDay: { limit: number; used: number; resetsAt: string }
  }
  amount: {
    userBudgetEur: { limit: number; used: number; ratio: number; resetsAt: string }
    globalBudgetEur: { limit: number; used: number; ratio: number; resetsAt: string }
  }
  note?: string
}

export function fetchGuardrailsConfig(): Promise<GuardrailsConfig> {
  return apiFetch<GuardrailsConfig>('/admin/guardrails/config')
}

export function updateGuardrailsConfig(body: Partial<GuardrailsConfig>): Promise<GuardrailsConfig> {
  return apiFetch<GuardrailsConfig>('/admin/guardrails/config', {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function fetchGuardrailsStatus(): Promise<GuardrailsStatus> {
  return apiFetch<GuardrailsStatus>('/admin/guardrails/status')
}
