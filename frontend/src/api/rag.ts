import { apiFetch } from './client'
import type { ApiRagDocument } from './types'

export function uploadRagDocument(file: File): Promise<ApiRagDocument> {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<ApiRagDocument>('/rag/documents', { method: 'POST', body })
}

export function fetchRagDocuments(): Promise<ApiRagDocument[]> {
  return apiFetch<ApiRagDocument[]>('/rag/documents')
}

export function deleteRagDocument(id: number): Promise<void> {
  return apiFetch<void>(`/rag/documents/${id}`, { method: 'DELETE' })
}

export function fetchConversationRagDocuments(conversationId: number): Promise<ApiRagDocument[]> {
  return apiFetch<ApiRagDocument[]>(`/conversations/${conversationId}/rag-documents`)
}

export function attachRagDocument(conversationId: number, documentId: number): Promise<ApiRagDocument> {
  return apiFetch<ApiRagDocument>(`/conversations/${conversationId}/rag-documents/${documentId}`, {
    method: 'POST',
  })
}

export function detachRagDocument(conversationId: number, documentId: number): Promise<void> {
  return apiFetch<void>(`/conversations/${conversationId}/rag-documents/${documentId}`, {
    method: 'DELETE',
  })
}
