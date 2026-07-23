import { apiFetch } from './client'

export type RoutingMode = 'SHADOW' | 'ACTIVE'
export type RoutingProfile = 'ECONOMY' | 'QUALITY' | 'SPEED' | 'BALANCED'

export interface RoutingConfig {
  enabled: boolean
  mode: RoutingMode
  profile: RoutingProfile
  maxRetries: number
  retryBackoffMs: number
  defaultContextChars: number
  profiles?: Record<string, { cost: number; quality: number; speed: number }>
  modelScores?: Record<string, { quality: number; speed: number }>
}

export interface RankedCandidate {
  modelId: number
  modelName: string
  providerName?: string
  estimatedCostEur: number
  costScore: number
  qualityScore: number
  speedScore: number
  totalScore: number
  scoreDetail: Record<string, number>
}

export interface EliminatedCandidate {
  modelId: number
  modelName: string
  providerName?: string
  reason: string
}

export interface RoutingDecision {
  mode: RoutingMode
  profile: RoutingProfile
  selectedModelName: string | null
  selectedModelId: number | null
  fallbackModelName: string | null
  selectedEstimatedCostEur: number
  fallbackEstimatedCostEur: number
  promptChars: number
  signals: Record<string, unknown>
  ranked: RankedCandidate[]
  eliminated: EliminatedCandidate[]
}

export interface ShadowStats {
  from: string
  to: string
  totalDecisions: number
  divergences: number
  divergencePct: number
  appliedCostEur: number
  selectedCostEur: number
  potentialSavingEur: number
  selectedModelDistribution: Record<string, number>
}

export function fetchRoutingConfig(): Promise<RoutingConfig> {
  return apiFetch<RoutingConfig>('/admin/routing/config')
}

export function updateRoutingConfig(body: Partial<RoutingConfig>): Promise<RoutingConfig> {
  return apiFetch<RoutingConfig>('/admin/routing/config', {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function simulateRouting(prompt: string, streaming = false): Promise<RoutingDecision> {
  return apiFetch<RoutingDecision>('/admin/routing/simulate', {
    method: 'POST',
    body: JSON.stringify({ prompt, streaming }),
  })
}

export function fetchShadowStats(days = 7): Promise<ShadowStats> {
  return apiFetch<ShadowStats>(`/admin/routing/shadow-stats?days=${days}`)
}
