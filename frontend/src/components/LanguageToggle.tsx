import { useI18n, type Locale } from '../i18n'

export function LanguageToggle() {
  const { locale, setLocale, common } = useI18n()

  function select(next: Locale) {
    if (next !== locale) setLocale(next)
  }

  return (
    <div className="prefs-lang" role="group" aria-label={common.language}>
      <button
        type="button"
        className={`prefs-lang__btn${locale === 'fr' ? ' prefs-lang__btn--active' : ''}`}
        onClick={() => select('fr')}
        aria-pressed={locale === 'fr'}
      >
        FR
      </button>
      <button
        type="button"
        className={`prefs-lang__btn${locale === 'en' ? ' prefs-lang__btn--active' : ''}`}
        onClick={() => select('en')}
        aria-pressed={locale === 'en'}
      >
        EN
      </button>
    </div>
  )
}
