import { withAuthHeaders } from '../auth/session'
import { apiFetch, ApiError } from './client'
import type { ApiAttachment } from './types'

/** POST /api/files — multipart upload. Backend validates real magic bytes, not the extension
 *  or the Content-Type this sends (defense in depth — see AttachmentService server-side). */
export function uploadFile(file: File): Promise<ApiAttachment> {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<ApiAttachment>('/files', { method: 'POST', body })
}

/**
 * GET /api/files/{id} as a Blob — NOT routed through apiFetch (which assumes/parses JSON
 * bodies) and NOT a plain `<img src>` (the endpoint needs `Authorization: Bearer`, which an
 * `<img>` tag can never send). Caller is responsible for `URL.revokeObjectURL` when done.
 */
export async function fetchAttachmentBlob(id: number): Promise<Blob> {
  const response = await fetch(`/api/files/${id}`, { headers: withAuthHeaders() })
  if (!response.ok) {
    throw new ApiError(`Erreur API ${response.status} sur /files/${id}`, response.status)
  }
  return response.blob()
}
