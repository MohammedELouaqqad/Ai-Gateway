import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'

interface DialogProps {
  open: boolean
  onClose: () => void
  title: string
  children: React.ReactNode
  /** Footer buttons/links — dialog renders none if omitted (informational-only case). */
  actions?: React.ReactNode
  closeLabel: string
}

/**
 * First real modal component in this app — everywhere else still uses window.confirm/alert
 * (conversations, templates, providers, API keys). Deliberately scoped to the one case that
 * actually needs it (blocked-model-delete explanation, which needs real hierarchy + a "view
 * usage" link that a native alert() can't offer) rather than migrating every confirm dialog at
 * once — see docs discussion. role="alertdialog": this always communicates a constraint the
 * user must acknowledge, not a routine yes/no choice.
 */
export function Dialog({ open, onClose, title, children, actions, closeLabel }: DialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    dialogRef.current?.focus()
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        className="dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        ref={dialogRef}
        tabIndex={-1}
      >
        <header className="dialog__header">
          <h2 id="dialog-title" className="dialog__title">{title}</h2>
          <button type="button" className="dialog__close" aria-label={closeLabel} onClick={onClose}>
            <X size={18} aria-hidden="true" />
          </button>
        </header>
        <div className="dialog__body">{children}</div>
        {actions && <footer className="dialog__actions">{actions}</footer>}
      </div>
    </div>
  )
}
