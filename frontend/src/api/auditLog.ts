import { apiFetch } from './client'

export interface AuditLogEntry {
  id: number
  actorUserId: number
  actorLabel: string
  action: string
  targetType: string
  targetId: number | null
  targetLabel: string | null
  details: Record<string, { old: unknown; new: unknown }> | null
  createdAt: string
}

export interface AuditLogPage {
  content: AuditLogEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditLogQuery {
  page?: number
  size?: number
  action?: string
  from?: string
  to?: string
}

function toParams(query: AuditLogQuery = {}): string {
  const p = new URLSearchParams()
  if (query.page != null) p.set('page', String(query.page))
  if (query.size != null) p.set('size', String(query.size))
  if (query.action) p.set('action', query.action)
  if (query.from) p.set('from', query.from)
  if (query.to) p.set('to', query.to)
  const s = p.toString()
  return s ? `?${s}` : ''
}

export function fetchAuditLog(query: AuditLogQuery = {}): Promise<AuditLogPage> {
  return apiFetch<AuditLogPage>(`/admin/audit-log${toParams(query)}`)
}

export const AUDIT_ACTIONS = [
  'USER_ENABLE',
  'USER_DISABLE',
  'PROVIDER_CREATE',
  'PROVIDER_UPDATE',
  'PROVIDER_DELETE',
  'PROVIDER_ENABLE',
  'PROVIDER_DISABLE',
  'MODEL_CREATE',
  'MODEL_UPDATE',
  'MODEL_DELETE',
  'MODEL_ENABLE',
  'MODEL_DISABLE',
  'MODEL_SET_DEFAULT',
  'GUARDRAILS_UPDATE',
] as const
