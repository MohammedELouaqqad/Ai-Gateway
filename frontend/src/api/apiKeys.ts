import { apiFetch } from './client'
import type { ApiApiKey, ApiApiKeyCreated, CreateApiKeyRequest } from './types'

export function fetchApiKeys(): Promise<ApiApiKey[]> {
  return apiFetch<ApiApiKey[]>('/api-keys')
}

export function createApiKey(request: CreateApiKeyRequest): Promise<ApiApiKeyCreated> {
  return apiFetch<ApiApiKeyCreated>('/api-keys', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function revokeApiKey(id: number): Promise<ApiApiKey> {
  return apiFetch<ApiApiKey>(`/api-keys/${id}/revoke`, { method: 'PATCH' })
}
