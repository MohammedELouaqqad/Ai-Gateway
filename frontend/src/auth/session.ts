const ACCESS_TOKEN_KEY = 'ai_gateway_access_token'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export function isAuthenticated(): boolean {
  return Boolean(getAccessToken())
}

/** Ajoute Authorization: Bearer si un token est présent. */
export function withAuthHeaders(headers?: HeadersInit): Headers {
  const result = new Headers(headers)
  const token = getAccessToken()
  if (token && !result.has('Authorization')) {
    result.set('Authorization', `Bearer ${token}`)
  }
  return result
}
