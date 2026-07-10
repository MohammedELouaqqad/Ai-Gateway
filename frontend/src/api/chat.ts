import { apiFetch } from './client'
import type { ChatRequest, ChatResponse } from './types'

export function sendChatMessage(request: ChatRequest, signal?: AbortSignal): Promise<ChatResponse> {
  return apiFetch<ChatResponse>('/chat', {
    method: 'POST',
    body: JSON.stringify(request),
    signal,
  })
}
