import { setAccessToken } from './session'

const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM ?? 'Ai-Gateway'
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'Ai-Gateway-frontend'

interface TokenResponse {
  access_token: string
  expires_in?: number
  refresh_token?: string
  token_type?: string
}

/**
 * Resource Owner Password Credentials (dev) via proxy Vite → Keycloak.
 * En prod, préférer Authorization Code + PKCE.
 */
export async function loginWithPassword(username: string, password: string): Promise<void> {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: KEYCLOAK_CLIENT_ID,
    username,
    password,
  })

  const response = await fetch(
    `/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    },
  )

  if (!response.ok) {
    let detail = `Échec connexion Keycloak (${response.status})`
    try {
      const err = (await response.json()) as { error_description?: string; error?: string }
      detail = err.error_description || err.error || detail
    } catch {
      /* ignore */
    }
    throw new Error(detail)
  }

  const data = (await response.json()) as TokenResponse
  if (!data.access_token) {
    throw new Error('Réponse Keycloak sans access_token')
  }
  setAccessToken(data.access_token)
}
