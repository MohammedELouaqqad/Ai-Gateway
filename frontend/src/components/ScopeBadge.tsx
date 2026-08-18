import type { ReactNode } from 'react'

type Scope = 'session' | 'range' | 'total'

const LABELS: Record<'fr' | 'en', Record<Scope, string>> = {
  fr: { session: 'session', range: 'plage', total: 'total' },
  en: { session: 'session', range: 'range', total: 'total' },
}

interface ScopeBadgeProps {
  scope: Scope
  locale?: 'fr' | 'en'
  children?: ReactNode
}

/** Explicit KPI scope label (session / date range / total). */
export function ScopeBadge({ scope, locale = 'fr', children }: ScopeBadgeProps) {
  const label = children ?? LABELS[locale][scope]
  return <span className={`scope-badge scope-badge--${scope}`}>{label}</span>
}
