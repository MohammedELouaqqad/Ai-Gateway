import { apiFetch } from './client'
import type { FeedbackRating } from './types'

export interface FeedbackResponse {
  id?: number
  messageId: number
  rating?: FeedbackRating
  myFeedback: FeedbackRating | null
  modelName?: string | null
  reason?: string | null
}

export function submitMessageFeedback(
  messageId: number,
  rating: FeedbackRating,
  reason?: string,
): Promise<FeedbackResponse> {
  return apiFetch<FeedbackResponse>(`/messages/${messageId}/feedback`, {
    method: 'POST',
    body: JSON.stringify({ rating, reason: reason ?? null }),
  })
}

export function clearMessageFeedback(messageId: number): Promise<FeedbackResponse> {
  return apiFetch<FeedbackResponse>(`/messages/${messageId}/feedback`, {
    method: 'DELETE',
  })
}

export function regenerateMessage(messageId: number): Promise<{ content: string }> {
  return apiFetch<{ content: string }>(`/messages/${messageId}/regenerate`, {
    method: 'POST',
  })
}
