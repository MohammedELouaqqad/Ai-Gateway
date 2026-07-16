import { clearAccessToken, withAuthHeaders } from '../auth/session'
import { ApiError } from './client'
import type { ChatChunk, ChatRequest, ChatResponse } from './types'

const API_BASE = '/api'

function handleUnauthorized(status: number): void {
  if (status === 401) {
    clearAccessToken()
    if (window.location.pathname !== '/login') {
      window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
    }
  }
}

/** Chat synchrone (POST /api/chat) — conservé. */
export function sendChatMessage(request: ChatRequest, signal?: AbortSignal): Promise<ChatResponse> {
  return fetchJson<ChatResponse>('/chat', {
    method: 'POST',
    body: JSON.stringify({ ...request, stream: false }),
    signal,
  })
}

export interface StreamHandlers {
  onChunk: (chunk: ChatChunk) => void
}

/**
 * Chat streaming SSE (POST /api/chat/stream).
 * Parse les events {@code chunk} émis par SseEmitter backend.
 */
export async function sendChatStream(
  request: ChatRequest,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE}/chat/stream`, {
    method: 'POST',
    headers: withAuthHeaders({
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    }),
    body: JSON.stringify({ ...request, stream: true }),
    signal,
  })

  if (!response.ok) {
    handleUnauthorized(response.status)
    let body: unknown
    try {
      body = await response.json()
    } catch {
      body = await response.text()
    }
    const apiMessage =
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof (body as { message: unknown }).message === 'string'
        ? (body as { message: string }).message
        : null
    const detail =
      response.status === 403
        ? apiMessage || 'Accès refusé — droits insuffisants'
        : apiMessage || `Erreur API ${response.status} sur /chat/stream`
    throw new ApiError(detail, response.status, body)
  }

  if (!response.body) {
    throw new ApiError('Réponse stream vide', response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = consumeSseBuffer(buffer, handlers.onChunk)
  }

  if (buffer.trim()) {
    consumeSseBuffer(`${buffer}\n\n`, handlers.onChunk)
  }
}

function consumeSseBuffer(buffer: string, onChunk: (chunk: ChatChunk) => void): string {
  const parts = buffer.split('\n\n')
  const rest = parts.pop() ?? ''

  for (const rawEvent of parts) {
    const chunk = parseSseEvent(rawEvent)
    if (chunk) onChunk(chunk)
  }
  return rest
}

function parseSseEvent(rawEvent: string): ChatChunk | null {
  const lines = rawEvent.split('\n')
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (dataLines.length === 0) return null
  const payload = dataLines.join('\n').trim()
  if (!payload || payload === '[DONE]') return null

  try {
    return JSON.parse(payload) as ChatChunk
  } catch {
    return null
  }
}

async function fetchJson<T>(path: string, options: RequestInit): Promise<T> {
  const headers = withAuthHeaders(options.headers)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (!response.ok) {
    handleUnauthorized(response.status)
    let body: unknown
    try {
      body = await response.json()
    } catch {
      body = await response.text()
    }
    const apiMessage =
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof (body as { message: unknown }).message === 'string'
        ? (body as { message: string }).message
        : null
    const detail =
      response.status === 403
        ? apiMessage || 'Accès refusé — droits insuffisants'
        : apiMessage || `Erreur API ${response.status} sur ${path}`
    throw new ApiError(detail, response.status, body)
  }

  if (response.status === 204 || response.status === 205) {
    return undefined as T
  }

  const text = await response.text()
  if (!text.trim()) return undefined as T
  return JSON.parse(text) as T
}
