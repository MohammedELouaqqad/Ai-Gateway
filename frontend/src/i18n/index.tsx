import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { fr, type Dictionary } from './fr'
import { en } from './en'

export type Locale = 'fr' | 'en'

const STORAGE_KEY = 'ai_gateway_locale'

const dictionaries: Record<Locale, Dictionary> = { fr, en }

function readStoredLocale(): Locale {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw === 'en' || raw === 'fr') return raw
  } catch {
    /* ignore */
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language.toLowerCase() : 'fr'
  return nav.startsWith('en') ? 'en' : 'fr'
}

function makeFmt(locale: Locale) {
  const tag = locale === 'en' ? 'en-US' : 'fr-FR'
  return {
    num: (n: number) => n.toLocaleString(tag),
    currency: (n: number) => {
      if (n === 0) return '0\u00a0$'
      if (n < 0.01) {
        return `${n.toLocaleString(tag, { minimumFractionDigits: 4, maximumFractionDigits: 6 })}\u00a0$`
      }
      return `${n.toLocaleString(tag, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}\u00a0$`
    },
  }
}

interface I18nContextValue {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: Dictionary
  nav: Dictionary['nav']
  navSections: Dictionary['navSections']
  pages: Dictionary['pages']
  common: Dictionary['common']
  labels: Dictionary['labels']
  fmt: ReturnType<typeof makeFmt>
}

const I18nContext = createContext<I18nContextValue | null>(null)

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => readStoredLocale())

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      /* ignore */
    }
  }, [])

  useEffect(() => {
    document.documentElement.lang = locale
  }, [locale])

  const value = useMemo<I18nContextValue>(() => {
    const t = dictionaries[locale]
    return {
      locale,
      setLocale,
      t,
      nav: t.nav,
      navSections: t.navSections,
      pages: t.pages,
      common: t.common,
      labels: t.labels,
      fmt: makeFmt(locale),
    }
  }, [locale, setLocale])

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext)
  if (!ctx) {
    throw new Error('useI18n must be used within LocaleProvider')
  }
  return ctx
}
