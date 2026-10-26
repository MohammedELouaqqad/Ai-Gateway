import { apiFetch } from './client'
import type {
  ApiPromptTemplate,
  CreatePromptTemplateRequest,
  UpdatePromptTemplateRequest,
} from './types'

export function fetchPromptTemplates(): Promise<ApiPromptTemplate[]> {
  return apiFetch<ApiPromptTemplate[]>('/prompt-templates')
}

export function createPromptTemplate(
  request: CreatePromptTemplateRequest,
): Promise<ApiPromptTemplate> {
  return apiFetch<ApiPromptTemplate>('/prompt-templates', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updatePromptTemplate(
  id: number,
  request: UpdatePromptTemplateRequest,
): Promise<ApiPromptTemplate> {
  return apiFetch<ApiPromptTemplate>(`/prompt-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deletePromptTemplate(id: number): Promise<void> {
  return apiFetch<void>(`/prompt-templates/${id}`, { method: 'DELETE' })
}
