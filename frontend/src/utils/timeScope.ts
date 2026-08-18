export type TimeScope = '24h' | '7d' | '30d' | 'session'

export function scopeToRange(scope: TimeScope): { from?: string; to?: string } {
  if (scope === 'session') return {}
  const to = new Date()
  const from = new Date(to)
  if (scope === '24h') from.setHours(from.getHours() - 24)
  else if (scope === '7d') from.setDate(from.getDate() - 7)
  else from.setDate(from.getDate() - 30)
  return { from: from.toISOString(), to: to.toISOString() }
}

export function scopeBadgeLabel(scope: TimeScope, locale: 'fr' | 'en'): string {
  const fr: Record<TimeScope, string> = {
    '24h': '24h',
    '7d': '7j',
    '30d': '30j',
    session: 'session',
  }
  const en: Record<TimeScope, string> = {
    '24h': '24h',
    '7d': '7d',
    '30d': '30d',
    session: 'session',
  }
  return (locale === 'en' ? en : fr)[scope]
}
