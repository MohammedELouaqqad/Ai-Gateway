import {
  clearAccessToken,
  clearIdToken,
  getAccessToken,
  getIdToken,
  setAccessToken,
  setIdToken,
} from './session'

const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM ?? 'Ai-Gateway'
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'Ai-Gateway-frontend'
/** URL publique Keycloak (navigateur) — pas le DNS Docker interne. */
const KEYCLOAK_URL = (import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180').replace(/\/$/, '')

const PKCE_VERIFIER_KEY = 'ai_gateway_pkce_verifier'
const OAUTH_STATE_KEY = 'ai_gateway_oauth_state'
const POST_LOGIN_REDIRECT_KEY = 'ai_gateway_post_login_redirect'
const CODE_USED_PREFIX = 'ai_gateway_oauth_code_used_'

interface TokenResponse {
  access_token: string
  expires_in?: number
  refresh_token?: string
  id_token?: string
  token_type?: string
}

function realmBase(): string {
  return `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`
}

function callbackUri(): string {
  return `${window.location.origin}/auth/callback`
}

function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function randomString(byteLength = 32): string {
  const bytes = new Uint8Array(byteLength)
  crypto.getRandomValues(bytes)
  return base64UrlEncode(bytes.buffer)
}

async function sha256Base64Url(value: string): Promise<string> {
  const data = new TextEncoder().encode(value)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return base64UrlEncode(digest)
}

/**
 * Prépare et lance une redirection Authorization Code + PKCE vers Keycloak, sur
 * `endpoint` (`auth` pour le login, `registrations` pour l'inscription — même mécanisme,
 * seul le endpoint hébergé par Keycloak diffère). Plus de formulaire maison dans React.
 */
async function beginAuthorizationCodeFlow(endpoint: 'auth' | 'registrations', postLoginRedirect: string): Promise<void> {
  const verifier = randomString(32)
  const state = randomString(16)
  const challenge = await sha256Base64Url(verifier)

  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier)
  sessionStorage.setItem(OAUTH_STATE_KEY, state)
  sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, postLoginRedirect || '/')

  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    redirect_uri: callbackUri(),
    response_type: 'code',
    scope: 'openid profile email',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })

  window.location.assign(`${realmBase()}/${endpoint}?${params.toString()}`)
}

/** Redirige vers la page de login Keycloak. */
export function beginLogin(postLoginRedirect = '/'): Promise<void> {
  return beginAuthorizationCodeFlow('auth', postLoginRedirect)
}

/**
 * Redirige vers la page d'inscription native Keycloak (`/protocol/openid-connect/registrations`
 * — même flow que le login, juste un endpoint différent, exactement ce que fait `keycloak.register()`
 * dans keycloak-js). Si `registrationAllowed` est à `false` sur le realm, Keycloak retombe de
 * lui-même sur l'écran de login — rien à répliquer côté app (vérifié empiriquement).
 * Retour ensuite identique à un login : `/auth/callback` puis authentifié dans l'app.
 */
export function beginRegister(postLoginRedirect = '/'): Promise<void> {
  return beginAuthorizationCodeFlow('registrations', postLoginRedirect)
}

/** Déduplique l’échange (React Strict Mode monte 2× le callback en dev). */
let pendingCodeExchange: { search: string; promise: Promise<string> } | null = null

/**
 * Échange le `code` OAuth contre des tokens (appelé depuis /auth/callback).
 * Un code Keycloak est à usage unique — un 2e POST → "Code not valid".
 * @returns chemin de redirection post-login
 */
export async function completeLoginFromCallback(search: string): Promise<string> {
  const normalized = search.startsWith('?') ? search : `?${search}`
  if (pendingCodeExchange?.search === normalized) {
    return pendingCodeExchange.promise
  }

  const promise = exchangeAuthorizationCode(normalized).finally(() => {
    if (pendingCodeExchange?.promise === promise) pendingCodeExchange = null
  })
  pendingCodeExchange = { search: normalized, promise }
  return promise
}

async function exchangeAuthorizationCode(search: string): Promise<string> {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search)
  const error = params.get('error')
  if (error) {
    const desc = params.get('error_description') || error
    throw new Error(desc)
  }

  const code = params.get('code')
  const state = params.get('state')
  const expectedState = sessionStorage.getItem(OAUTH_STATE_KEY)
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY)

  // Rechargement / remount après un échange déjà réussi
  if (code && sessionStorage.getItem(`${CODE_USED_PREFIX}${code}`) && getAccessToken()) {
    const redirect = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY) || '/'
    return redirect.startsWith('/') ? redirect : '/'
  }

  if (!code || !state || !expectedState || state !== expectedState) {
    throw new Error('État OAuth invalide — relancez la connexion')
  }
  if (!verifier) {
    throw new Error('PKCE verifier manquant — relancez la connexion')
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: KEYCLOAK_CLIENT_ID,
    code,
    redirect_uri: callbackUri(),
    code_verifier: verifier,
  })

  // Via proxy Vite /realms → Keycloak (évite CORS navigateur)
  const response = await fetch(`/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })

  if (!response.ok) {
    let detail = `Échec échange token Keycloak (${response.status})`
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
  if (data.id_token) setIdToken(data.id_token)
  sessionStorage.setItem(`${CODE_USED_PREFIX}${code}`, '1')

  sessionStorage.removeItem(PKCE_VERIFIER_KEY)
  sessionStorage.removeItem(OAUTH_STATE_KEY)
  const redirect = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY) || '/'
  sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
  return redirect.startsWith('/') ? redirect : '/'
}

/**
 * URL de l'Account Console Keycloak (self-service) — mot de passe, nom, email, sessions actives.
 * Aucun formulaire à construire côté app : la même session SSO déjà active dans le navigateur
 * (celle qui a produit le JWT) s'applique, pas de re-login.
 * `section` : chemin vérifié contre le bundle réel de la console (Keycloak 26, thème
 * `keycloak.v3`) — `"account-security/signing-in"` existe ; les infos personnelles, elles,
 * sont la page racine (pas de sous-route dédiée dans ce build).
 */
export function accountConsoleUrl(section?: 'account-security/signing-in'): string {
  return `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/account/${section ?? ''}`
}

/** Déconnexion locale + page logout Keycloak. */
export function logout(): void {
  const idToken = getIdToken()
  clearAccessToken()
  clearIdToken()

  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    post_logout_redirect_uri: window.location.origin + '/',
  })
  if (idToken) params.set('id_token_hint', idToken)

  window.location.assign(`${realmBase()}/logout?${params.toString()}`)
}

export { KEYCLOAK_CLIENT_ID, KEYCLOAK_REALM, KEYCLOAK_URL }
