/** Limites alignées sur le backend ContentLimits. */
export const CONTENT_LIMITS = {
  TEMPLATE_NAME_MAX: 100,
  TEMPLATE_DESCRIPTION_MAX: 500,
  TEMPLATE_CONTENT_MAX: 20_000,
  CHAT_MESSAGE_MAX: 50_000,
  CHAT_MESSAGE_WARN: 40_000,
} as const

export function truncatePreview(text: string, max = 80): string {
  const value = text.trim()
  if (value.length <= max) return value
  return `${value.slice(0, max)}…`
}

/**
 * Applique un template : remplace {{content}} / {{input}} / {{text}}
 * par la saisie courante, sinon concatène template + saisie.
 */
export function applyPromptTemplate(templateContent: string, userInput: string): string {
  const placeholders = ['{{content}}', '{{input}}', '{{text}}'] as const
  const hasPlaceholder = placeholders.some((p) => templateContent.includes(p))
  const input = userInput.trim()

  if (hasPlaceholder) {
    let result = templateContent
    for (const placeholder of placeholders) {
      result = result.split(placeholder).join(input)
    }
    return result
  }

  if (!input) return templateContent
  return `${templateContent}\n\n${input}`
}

export function formatCharCount(current: number, max: number): string {
  return `${current.toLocaleString('fr-FR')} / ${max.toLocaleString('fr-FR')}`
}

/**
 * Rough client-side token estimate (~4 chars/token, the commonly cited approximation for
 * English/French mixed text) — NOT tied to any specific model's real context window, which
 * this gateway doesn't currently track per model. Purely presentational: reformats the
 * existing character-based limit (CHAT_MESSAGE_MAX) as an approximate token count so the
 * composer counter reads like a market-standard "N / M tokens" signal instead of raw
 * characters, without adding any new state, request, or backend field.
 */
const CHARS_PER_TOKEN_ESTIMATE = 4

export function estimateTokenCount(chars: number): number {
  return Math.ceil(chars / CHARS_PER_TOKEN_ESTIMATE)
}

export function formatTokenCount(currentChars: number, maxChars: number): string {
  const current = estimateTokenCount(currentChars)
  const max = estimateTokenCount(maxChars)
  return `~${current.toLocaleString('fr-FR')} / ${max.toLocaleString('fr-FR')} tokens`
}
