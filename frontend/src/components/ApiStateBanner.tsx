interface ApiStateBannerProps {
  loading?: boolean
  error?: string | null
}

export function ApiStateBanner({ loading, error }: ApiStateBannerProps) {
  if (!loading && !error) return null

  return (
    <div
      className={`api-banner${error ? ' api-banner--error' : ' api-banner--loading'}`}
      role={error ? 'alert' : 'status'}
      aria-live={error ? 'assertive' : 'polite'}
    >
      {loading && !error && (
        <>
          <span className="api-banner__spinner" aria-hidden="true" />
          <span>Chargement…</span>
        </>
      )}
      {error && <span>{error}</span>}
    </div>
  )
}
