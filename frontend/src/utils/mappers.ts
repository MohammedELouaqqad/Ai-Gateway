import type { ApiModel, ApiProvider } from '../api/types'

export function isModelDefault(model: ApiModel): boolean {
  return model.default ?? model.isDefault ?? false
}

export function providerToUiType(provider?: ApiProvider): 'onprem' | 'cloud' {
  if (!provider) return 'onprem'
  if (provider.location === 'LOCAL') return 'onprem'
  if (provider.type === 'OLLAMA' || provider.type === 'SGLANG' || provider.type === 'VLLM') {
    return 'onprem'
  }
  return 'cloud'
}

export function formatShortDate(iso?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  return date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
}

export function formatDateTime(iso?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  return date.toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso?: string): string {
  if (!iso) return ''
  const date = new Date(iso)
  return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
}
