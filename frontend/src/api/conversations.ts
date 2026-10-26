import { apiFetch } from './client'
import type { ApiConversation, ApiMessage, RenameConversationRequest } from './types'

export function fetchConversations(): Promise<ApiConversation[]> {
  return apiFetch<ApiConversation[]>('/conversations')
}

export function createConversation(): Promise<ApiConversation> {
  return apiFetch<ApiConversation>('/conversations', { method: 'POST' })
}

export function renameConversation(
  id: number,
  body: RenameConversationRequest,
): Promise<ApiConversation> {
  return apiFetch<ApiConversation>(`/conversations/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function deleteConversation(id: number): Promise<void> {
  return apiFetch<void>(`/conversations/${id}`, { method: 'DELETE' })
}

export function fetchMessages(conversationId: number): Promise<ApiMessage[]> {
  return apiFetch<ApiMessage[]>(`/conversations/${conversationId}/messages`)
}
