import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../theme/ThemeProvider'
import { useI18n } from '../i18n'

/** Bouton significatif : soleil = passer en clair, lune = passer en sombre. */
export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const { common } = useI18n()
  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      className="prefs-toggle prefs-toggle--theme"
      onClick={toggleTheme}
      aria-label={isDark ? common.themeLight : common.themeDark}
      title={isDark ? common.themeLightLabel : common.themeDarkLabel}
    >
      <span className="prefs-toggle__icon" aria-hidden="true">
        {isDark ? <Sun size={16} strokeWidth={1.75} /> : <Moon size={16} strokeWidth={1.75} />}
      </span>
      <span className="prefs-toggle__label">
        {isDark ? common.themeLightLabel : common.themeDarkLabel}
      </span>
    </button>
  )
}
