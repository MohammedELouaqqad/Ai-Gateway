import { useEffect, useRef, useState } from 'react'
import { MoreVertical, Pencil, Power, Star, Trash2 } from 'lucide-react'
import type { ApiModel } from '../api/types'
import { useI18n } from '../i18n'

const ICON_SIZE = 16

interface ModelRowActionsProps {
  model: ApiModel
  isDefault: boolean
  providerEnabled?: boolean
  /** Si false, aucune action admin n'est affichée (lecture seule). */
  canMutate?: boolean
  onRename: () => void
  onSetDefault: () => void
  onToggle: () => void
  onDelete: () => void
}

export function ModelRowActions({
  model,
  isDefault,
  providerEnabled = true,
  canMutate = true,
  onRename,
  onSetDefault,
  onToggle,
  onDelete,
}: ModelRowActionsProps) {
  const { common, pages } = useI18n()
  const m = pages.models
  const [menuOpen, setMenuOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const label = model.displayName ?? model.name
  const canEnable = providerEnabled
  const toggleDisabled = !model.enabled && !canEnable

  const defaultTitle = isDefault
    ? m.alreadyDefault
    : !model.enabled
      ? m.enableBeforeDefault
      : !providerEnabled
        ? m.providerInactive
        : common.setDefault

  const toggleTitle = model.enabled
    ? common.disable
    : !canEnable
      ? m.enableProviderFirst
      : common.enable

  // A model still referenced by conversations/usage logs (live FKs, not snapshots) can't be
  // deleted while it's enabled — deleting it out from under an active model would either fail
  // on the FK constraint or, worse, orphan real history. Disabling first is the safe path, and
  // it's the same disabled-button pattern already used for isDefault below.
  const deleteBlockedByEnabled = model.enabled
  const deleteTitle = isDefault
    ? m.removeDefaultBeforeDelete
    : deleteBlockedByEnabled
      ? m.disableBeforeDelete
      : m.deleteModel

  useEffect(() => {
    if (!menuOpen) return
    function handleClickOutside(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [menuOpen])

  function runAction(action: () => void) {
    setMenuOpen(false)
    action()
  }

  if (!canMutate) {
    return <span className="mono" style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>—</span>
  }

  return (
    <div className="row-actions-wrap" ref={rootRef}>
      <div
        className="row-actions row-actions--toolbar"
        role="toolbar"
        aria-label={m.actionsFor.replace('{name}', label)}
      >
        <button
          type="button"
          className="icon-btn icon-btn--muted"
          title={common.rename}
          aria-label={`${common.rename} ${label}`}
          onClick={() => onRename()}
        >
          <Pencil size={ICON_SIZE} aria-hidden="true" />
        </button>

        <span className="row-actions__sep" aria-hidden="true" />

        <div className="row-actions__default">
          <button
            type="button"
            className={`icon-btn icon-btn--star${isDefault ? ' icon-btn--star-active' : ''}`}
            disabled={isDefault || !model.enabled || !providerEnabled}
            title={defaultTitle}
            aria-label={common.setDefault}
            onClick={() => onSetDefault()}
          >
            <Star
              size={ICON_SIZE}
              aria-hidden="true"
              fill={isDefault ? 'currentColor' : 'none'}
            />
          </button>
          {isDefault && <span className="row-actions__default-label">{common.default}</span>}
        </div>

        <button
          type="button"
          className={`icon-btn ${model.enabled ? 'icon-btn--power-on' : 'icon-btn--power-off'}`}
          title={toggleTitle}
          aria-label={toggleTitle}
          disabled={toggleDisabled}
          onClick={() => onToggle()}
        >
          <Power size={ICON_SIZE} aria-hidden="true" />
        </button>

        <span className="row-actions__sep" aria-hidden="true" />

        <button
          type="button"
          className="icon-btn icon-btn--danger"
          disabled={isDefault || deleteBlockedByEnabled}
          title={deleteTitle}
          aria-label={common.delete}
          onClick={() => onDelete()}
        >
          <Trash2 size={ICON_SIZE} aria-hidden="true" />
        </button>
      </div>

      <div className="row-actions row-actions--menu">
        <button
          type="button"
          className="icon-btn icon-btn--muted"
          title={common.actions}
          aria-label={m.menuFor.replace('{name}', label)}
          aria-expanded={menuOpen}
          aria-haspopup="menu"
          onClick={() => setMenuOpen((prev) => !prev)}
        >
          <MoreVertical size={ICON_SIZE} aria-hidden="true" />
        </button>

        {menuOpen && (
          <div className="row-actions__dropdown" role="menu">
            <button
              type="button"
              className="row-actions__dropdown-item"
              role="menuitem"
              onClick={() => runAction(onRename)}
            >
              <Pencil size={ICON_SIZE} aria-hidden="true" />
              {common.rename}
            </button>
            <button
              type="button"
              className="row-actions__dropdown-item"
              role="menuitem"
              disabled={isDefault || !model.enabled || !providerEnabled}
              onClick={() => runAction(onSetDefault)}
            >
              <Star size={ICON_SIZE} aria-hidden="true" />
              {common.setDefault}
            </button>
            <button
              type="button"
              className="row-actions__dropdown-item"
              role="menuitem"
              disabled={toggleDisabled}
              title={toggleTitle}
              onClick={() => runAction(onToggle)}
            >
              <Power size={ICON_SIZE} aria-hidden="true" />
              {model.enabled ? common.disable : common.enable}
            </button>
            <button
              type="button"
              className="row-actions__dropdown-item row-actions__dropdown-item--danger"
              role="menuitem"
              disabled={isDefault || deleteBlockedByEnabled}
              title={deleteTitle}
              onClick={() => runAction(onDelete)}
            >
              <Trash2 size={ICON_SIZE} aria-hidden="true" />
              {common.delete}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
