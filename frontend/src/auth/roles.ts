import { getAccessToken } from './session'

export type AppRole = 'ADMIN' | 'DEVELOPER' | 'USER' | 'SERVICE_ACCOUNT'

interface JwtPayload {
  realm_access?: { roles?: string[] }
  resource_access?: Record<string, { roles?: string[] }>
  preferred_username?: string
  exp?: number
}

function decodeBase64Url(input: string): string {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/')
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4))
  return atob(padded + pad)
}

export function parseAccessToken(): JwtPayload | null {
  const token = getAccessToken()
  if (!token) return null
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    return JSON.parse(decodeBase64Url(parts[1])) as JwtPayload
  } catch {
    return null
  }
}

/** Rôles Keycloak realm (+ client) sans préfixe ROLE_. */
export function getRoles(): string[] {
  const payload = parseAccessToken()
  if (!payload) return []
  const roles = new Set<string>()
  for (const role of payload.realm_access?.roles ?? []) {
    roles.add(role)
  }
  for (const client of Object.values(payload.resource_access ?? {})) {
    for (const role of client.roles ?? []) {
      roles.add(role)
    }
  }
  return [...roles]
}

export function hasRole(role: AppRole | string): boolean {
  return getRoles().includes(role)
}

export function isAdmin(): boolean {
  return hasRole('ADMIN')
}

export function isTokenExpired(): boolean {
  const payload = parseAccessToken()
  if (!payload?.exp) return false
  return payload.exp * 1000 <= Date.now()
}
