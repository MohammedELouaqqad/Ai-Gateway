/** Compact token count: 1396 → "1.4K", 1_200_000 → "1.2M". */
export function formatTokens(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return '—'
  const abs = Math.abs(n)
  if (abs >= 1_000_000) {
    const v = n / 1_000_000
    return `${trimOneDecimal(v)}M`
  }
  if (abs >= 1_000) {
    const v = n / 1_000
    return `${trimOneDecimal(v)}K`
  }
  return String(Math.round(n))
}

function trimOneDecimal(v: number): string {
  const rounded = Math.round(v * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}
