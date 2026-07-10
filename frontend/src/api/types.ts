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
}

export interface ApiModel {
  id: number
  displayName: string
  name: string
  enabled: boolean
  default: boolean
  isDefault?: boolean
  createdAt?: string
  provider?: ApiProvider
}

export interface CreateModelRequest {
  displayName: string
  name: string
  providerId: number
  enabled?: boolean
  isDefault?: boolean
}

export interface UpdateModelRequest {
  displayName: string
  name: string
}

export interface UpdateProviderRequest {
  name: string
  baseUrl?: string
  priority?: number
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
  conversation?: { id: number }
}

export interface ChatRequest {
  conversationId?: number | null
  content: string
  model: string
  stream?: boolean
}

export interface ChatResponse {
  content: string
  promptTokens: number | null
  completionTokens: number | null
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
