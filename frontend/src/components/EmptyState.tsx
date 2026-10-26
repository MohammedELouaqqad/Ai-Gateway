import { Link } from 'react-router-dom'
import { Badge } from './Badge'

interface EmptyStateProps {
  title?: string
  message: string
  /** Roadmap = pas encore branché ; info = état neutre */
  status?: 'roadmap' | 'info'
  action?: { to: string; label: string }
}

export function EmptyState({ title, message, status = 'info', action }: EmptyStateProps) {
  return (
    <div className="empty-state" role="status">
      <div className="empty-state__icon" aria-hidden="true" />
      {status === 'roadmap' && (
        <div className="empty-state__badge">
          <Badge variant="roadmap">Roadmap</Badge>
        </div>
      )}
      {title && <h2 className="empty-state__title">{title}</h2>}
      <p className="empty-state__message">{message}</p>
      {action && (
        <Link to={action.to} className="btn btn--secondary btn--sm empty-state__action">
          {action.label}
        </Link>
      )}
    </div>
  )
}
