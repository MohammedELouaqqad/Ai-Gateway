import { useMemo } from 'react'

export interface TrendSeries {
  key: string
  label: string
  color: string
  values: number[]
}

interface TrendChartProps {
  days: string[]
  series: TrendSeries[]
  height?: number
  emptyLabel: string
  formatY?: (n: number) => string
}

/** Lightweight multi-series SVG line chart (no chart library). */
export function TrendChart({
  days,
  series,
  height = 200,
  emptyLabel,
  formatY = (n) => String(Math.round(n)),
}: TrendChartProps) {
  const width = 640
  const pad = { top: 16, right: 12, bottom: 28, left: 48 }
  const innerW = width - pad.left - pad.right
  const innerH = height - pad.top - pad.bottom

  const { maxY, points } = useMemo(() => {
    const all = series.flatMap((s) => s.values)
    const max = Math.max(0, ...all)
    const maxY = max === 0 ? 1 : max * 1.1
    const n = Math.max(days.length, 1)
    const points = series.map((s) => {
      const coords = s.values.map((v, i) => {
        const x = pad.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW)
        const y = pad.top + innerH - (v / maxY) * innerH
        return { x, y, v }
      })
      const d = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
      return { ...s, coords, d }
    })
    return { maxY, points }
  }, [days.length, series, innerH, innerW, pad.left, pad.top])

  if (days.length === 0) {
    return <p className="table-empty">{emptyLabel}</p>
  }

  const yTicks = [0, 0.5, 1].map((t) => ({
    y: pad.top + innerH * (1 - t),
    label: formatY(maxY * t),
  }))

  return (
    <div className="chart-container">
      <svg className="chart-svg" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="trend">
        {yTicks.map((t) => (
          <g key={t.y}>
            <line
              x1={pad.left}
              x2={width - pad.right}
              y1={t.y}
              y2={t.y}
              stroke="var(--border-subtle)"
              strokeWidth={1}
            />
            <text
              x={pad.left - 6}
              y={t.y + 3}
              textAnchor="end"
              fill="var(--text-muted)"
              fontSize={10}
              fontFamily="var(--font-mono)"
            >
              {t.label}
            </text>
          </g>
        ))}
        {points.map((s) => (
          <path key={s.key} d={s.d} fill="none" stroke={s.color} strokeWidth={2} strokeLinejoin="round" />
        ))}
        {days.map((day, i) => {
          const n = days.length
          const x = pad.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW)
          const show = n <= 8 || i === 0 || i === n - 1 || i % Math.ceil(n / 6) === 0
          if (!show) return null
          return (
            <text
              key={day}
              x={x}
              y={height - 8}
              textAnchor="middle"
              fill="var(--text-muted)"
              fontSize={9}
              fontFamily="var(--font-mono)"
            >
              {day.slice(5)}
            </text>
          )
        })}
      </svg>
      <div className="chart-legend">
        {series.map((s) => (
          <span key={s.key} className="chart-legend__item">
            <span className="chart-legend__swatch" style={{ background: s.color }} />
            {s.label}
          </span>
        ))}
      </div>
    </div>
  )
}
