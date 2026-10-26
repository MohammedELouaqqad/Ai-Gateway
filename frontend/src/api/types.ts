export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

export type ProviderType = 'OPENAI' | 'AZURE_OPENAI' | 'SGLANG' | 'VLLM' | 'OLLAMA'
export type ProviderLocation = 'CLOUD' | 'LOCAL'

export interface ApiUser {
  id: number
  username?: string
  email?: string
}

export interface ApiProvider {
  id: number
  name: string
  baseUrl?: string
  type: ProviderType
  location: ProviderLocation
  enabled: boolean
  priority?: number
  createdAt?: string
  /** Never the key itself (never sent by the API) — just whether one is configured. */
  apiKeyPresent?: boolean
}

export interface ApiModel {
  id: number
  displayName: string
  name: string
  enabled: boolean
  default: boolean
  isDefault?: boolean
  /** Adaptateur stream présent dans ce gateway (Ollama/OpenAI/Azure = true). */
  supportsStreaming?: boolean
  /** Adaptateur chat présent — sinon le modèle reste listé mais inutilisable. */
  available?: boolean
  /** Admin-configurable — pas déduit du provider (deux modèles du même provider peuvent
   *  différer). Si absent/false, l'envoi d'une image vers ce modèle est refusé (400). */
  visionCapable?: boolean
  createdAt?: string
  provider?: ApiProvider
}

/** How many conversations/usage logs reference this model — see GET /models/{id}/usage-summary. */
export interface ApiModelUsageSummary {
  conversationCount: number
  usageLogCount: number
}

export interface CreateModelRequest {
  displayName: string
  name: string
  providerId: number
  enabled?: boolean
  isDefault?: boolean
  visionCapable?: boolean
}

export interface UpdateModelRequest {
  displayName: string
  name: string
  providerId: number
  visionCapable?: boolean
}

/** Réponse de POST /api/files — jamais les octets bruts (voir GET /api/files/{id} pour ça). */
export interface ApiAttachment {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  createdAt?: string
}

export type RagDocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

/** RAG document (Phase 1+2 — ingestion + retrieval). Attach to a conversation to make its
 *  content available to the model as retrieved context (see backend RagRetrievalService). */
export interface ApiRagDocument {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  status: RagDocumentStatus
  chunkCount: number
  errorMessage?: string | null
  createdAt?: string
  processedAt?: string | null
}

export interface UpdateProviderRequest {
  name: string
  baseUrl?: string
  location?: ProviderLocation
  priority?: number
  /** Omit/blank = leave the stored key untouched (never clears it — see backend for why). */
  apiKey?: string
}

export interface CreateProviderRequest {
  name: string
  baseUrl?: string
  type: ProviderType
  location: ProviderLocation
  enabled?: boolean
  priority?: number
  /** Optional — Ollama needs none, vLLM/SGLang treat it as optional too. */
  apiKey?: string
}

export interface ApiConversation {
  id: number
  title: string
  createdAt?: string
  updatedAt?: string
  user?: ApiUser
  model?: ApiModel
}

export interface ApiMessage {
  id: number
  role: MessageRole
  content: string
  createdAt?: string
  modelName?: string | null
  modelDisplayName?: string | null
  /** Current user's thumbs on this assistant message (null if none). */
  myFeedback?: FeedbackRating | null
  regenerated?: boolean
  supersededByMessageId?: number | null
  conversation?: { id: number }
  /** Non-null si cette réponse avait une image jointe — fetch via GET /api/files/{id}. */
  attachmentId?: number | null
  /** Nombre de passages RAG réellement injectés pour cette réponse (0 = aucun contexte utilisé). */
  ragChunksUsed?: number
}

export type FeedbackRating = 'UP' | 'DOWN'

export interface ChatRequest {
  conversationId?: number | null
  content: string
  model?: string | null
  stream?: boolean
  /** Id d'un fichier uploadé via POST /api/files. Refusé (400) si le modèle n'est pas
   *  visionCapable — jamais silencieusement ignoré. */
  attachmentId?: number | null
}

export interface ChatResponse {
  content: string
  promptTokens: number | null
  completionTokens: number | null
}

/** Format pivot SSE (backend ChatChunk). */
export interface ChatChunk {
  content: string
  done: boolean
  finishReason?: string | null
  usage?: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  } | null
}

export interface UsageSummary {
  totalTokens: number
  totalCost: number
  requestCount: number
}

export interface ApiUsageLog {
  id: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimatedCost: number
  latencyMs?: number
  status?: 'SUCCESS' | 'ERROR' | 0 | 1 | string
  createdAt?: string
  model?: ApiModel
  provider?: ApiProvider
  user?: ApiUser
}

export interface RenameConversationRequest {
  title: string
}

export interface ApiPromptTemplate {
  id: number
  name: string
  description?: string | null
  content: string
  createdAt?: string
}

export interface CreatePromptTemplateRequest {
  name: string
  description?: string
  content: string
}

export interface UpdatePromptTemplateRequest {
  name: string
  description?: string
  content: string
}

/** Never carries the secret — GET/PATCH always return this shape. */
export interface ApiApiKey {
  id: number
  name: string
  keyPrefix: string
  enabled: boolean
  createdAt?: string
  /** null/undefined = never expires. */
  expiresAt?: string | null
}

/** POST response only, once — {@link rawKey} is never retrievable again after this. */
export interface ApiApiKeyCreated extends ApiApiKey {
  rawKey: string
}

export interface CreateApiKeyRequest {
  name: string
  /** Optional TTL in days — omit/undefined for a key that never expires. */
  expiresInDays?: number
}

/**
 * Local user management only — roles are NOT here, they live exclusively in Keycloak
 * (JWT claims). This is id/username/email/enabled/createdAt from our own `users` table.
 */
export interface ApiAdminUser {
  id: number
  keycloakId?: string
  username?: string
  email?: string
  enabled: boolean
  createdAt?: string
}
