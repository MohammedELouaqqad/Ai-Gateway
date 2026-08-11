import { apiFetch } from './client'
import type { ApiProvider, CreateProviderRequest, UpdateProviderRequest } from './types'

export function fetchProviders(): Promise<ApiProvider[]> {
  return apiFetch<ApiProvider[]>('/providers')
}

export function createProvider(provider: CreateProviderRequest): Promise<ApiProvider> {
  return apiFetch<ApiProvider>('/providers', {
    method: 'POST',
    body: JSON.stringify({ enabled: false, ...provider }),
  })
}

export function updateProvider(id: number, request: UpdateProviderRequest): Promise<ApiProvider> {
  return apiFetch<ApiProvider>(`/providers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteProvider(id: number): Promise<void> {
  return apiFetch<void>(`/providers/${id}`, { method: 'DELETE' })
}

export function toggleProviderStatus(id: number): Promise<ApiProvider> {
  return apiFetch<ApiProvider>(`/providers/${id}/status`, { method: 'PATCH' })
}
