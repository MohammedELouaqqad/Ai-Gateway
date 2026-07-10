export type AppEnv = 'DEV' | 'STAGING' | 'PRODUCTION'

export function getAppEnv(): AppEnv {
  if (import.meta.env.DEV) return 'DEV'
  const env = import.meta.env.VITE_APP_ENV
  if (env === 'staging') return 'STAGING'
  return 'PRODUCTION'
}

export function envBadgeClass(env: AppEnv): string {
  if (env === 'DEV') return 'header__env-badge header__env-badge--dev'
  if (env === 'STAGING') return 'header__env-badge header__env-badge--staging'
  return 'header__env-badge header__env-badge--prod'
}
