export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public body?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const API_BASE = '/api'

async function readResponseBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text.trim()) {
    return undefined
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    const body = await readResponseBody(response)
    const detail =
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof (body as { message: unknown }).message === 'string'
        ? (body as { message: string }).message
        : `Erreur API ${response.status} sur ${path}`
    throw new ApiError(detail, response.status, body)
  }

  if (response.status === 204 || response.status === 205) {
    return undefined as T
  }

  const body = await readResponseBody(response)
  if (body === undefined) {
    return undefined as T
  }

  return body as T
}
