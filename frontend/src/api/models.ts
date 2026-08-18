import { apiFetch } from './client'
import type { ApiModel, ApiModelUsageSummary, CreateModelRequest, UpdateModelRequest } from './types'

export function fetchModels(): Promise<ApiModel[]> {
  return apiFetch<ApiModel[]>('/models')
}

/** How many conversations/usage logs still reference this model — fetched on demand right
 *  before offering delete, so the confirmation can show real counts instead of a raw backend
 *  error after a failed attempt. */
export function fetchModelUsageSummary(id: number): Promise<ApiModelUsageSummary> {
  return apiFetch<ApiModelUsageSummary>(`/models/${id}/usage-summary`)
}

export function createModel(request: CreateModelRequest): Promise<ApiModel> {
  return apiFetch<ApiModel>('/models', {
    method: 'POST',
    body: JSON.stringify({
      enabled: false,
      isDefault: false,
      ...request,
    }),
  })
}

export function updateModel(id: number, request: UpdateModelRequest): Promise<ApiModel> {
  return apiFetch<ApiModel>(`/models/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteModel(id: number): Promise<void> {
  return apiFetch<void>(`/models/${id}`, { method: 'DELETE' })
}

export function toggleModelStatus(id: number): Promise<ApiModel> {
  return apiFetch<ApiModel>(`/models/${id}/status`, { method: 'PATCH' })
}

export function setDefaultModel(id: number): Promise<ApiModel> {
  return apiFetch<ApiModel>(`/models/${id}/default`, { method: 'PATCH' })
}
