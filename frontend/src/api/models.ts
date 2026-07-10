import { apiFetch } from './client'
import type { ApiModel, CreateModelRequest, UpdateModelRequest } from './types'

export function fetchModels(): Promise<ApiModel[]> {
  return apiFetch<ApiModel[]>('/models')
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
