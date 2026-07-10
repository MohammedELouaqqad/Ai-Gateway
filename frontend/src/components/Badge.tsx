type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'secondary' | 'mvp' | 'roadmap'

interface BadgeProps {
  variant: BadgeVariant
  children: React.ReactNode
  dot?: boolean
  className?: string
}

export function Badge({ variant, children, dot, className }: BadgeProps) {
  return (
    <span className={`badge badge--${variant}${className ? ` ${className}` : ''}`}>
      {dot && <span className="badge__dot" aria-hidden="true" />}
      {children}
    </span>
  )
}
