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
