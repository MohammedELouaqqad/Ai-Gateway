import { apiFetch } from './client'

export type RoutingMode = 'SHADOW' | 'CANARY' | 'ACTIVE'
export type RoutingProfile = 'ECONOMY' | 'QUALITY' | 'SPEED' | 'BALANCED'

export interface RoutingConfig {
  enabled: boolean
  mode: RoutingMode
  canaryPercent?: number
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
  /** In SHADOW this is EXPECTED_IN_SHADOW — not a quality failure. */
  divergenceInterpretation?: string
  divergenceNote?: string
  appliedCostEur: number
  selectedCostEur: number
  potentialSavingEur: number
  potentialSavingNote?: string
  realizedAppliedCostEur?: number
  realizedProposedCostEur?: number
  realizedSavingEur?: number
  realizedCostPairs?: number
  selectedModelDistribution: Record<string, number>
  canaryLive?: number
  canaryHoldout?: number
  judgeReadyDisagreements?: number
  judgedCount?: number
  judgeProposedWins?: number
  judgeAppliedWins?: number
  judgeTies?: number
}

export interface JudgeRunResult {
  ok: boolean
  sampled?: number
  judgedOk?: number
  judgedError?: number
  mode?: string
  contract?: string
  error?: string
  results?: Array<Record<string, unknown>>
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

export function runRoutingJudge(limit = 5, mode = 'ALL'): Promise<JudgeRunResult> {
  return apiFetch<JudgeRunResult>(`/admin/routing/judge/run?limit=${limit}&mode=${mode}`, {
    method: 'POST',
  })
}

export type QualityStatus = 'MEASURED' | 'NOT_MEASURED'

export interface ModelQualityStats {
  modelName: string
  ups: number
  downs: number
  volume: number
  upRatio: number | null
  qualityStatus: QualityStatus
}

export interface ModelQualityResponse {
  models: ModelQualityStats[]
  overallQualityStatus: QualityStatus
  note?: string
}

export function fetchModelQuality(): Promise<ModelQualityResponse> {
  return apiFetch<ModelQualityResponse>('/admin/routing/model-quality')
}
