import { useEffect, useRef, useState } from 'react'
import { MoreVertical, Pencil, Power, Star, Trash2 } from 'lucide-react'
import type { ApiModel } from '../api/types'

const ICON_SIZE = 16

interface ModelRowActionsProps {
  model: ApiModel
  isDefault: boolean
  providerEnabled?: boolean
  onRename: () => void
  onSetDefault: () => void
  onToggle: () => void
  onDelete: () => void
}

export function ModelRowActions({
  model,
  isDefault,
  providerEnabled = true,
  onRename,
  onSetDefault,
  onToggle,
  onDelete,
}: ModelRowActionsProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const label = model.displayName ?? model.name
  const canEnable = providerEnabled
  const toggleDisabled = !model.enabled && !canEnable

  const defaultTitle = isDefault
    ? 'Ce modèle est déjà le défaut'
    : !model.enabled
      ? 'Activez le modèle avant de le définir par défaut'
      : !providerEnabled
        ? 'Le fournisseur est inactif'
        : 'Définir par défaut'

  const toggleTitle = model.enabled
    ? 'Désactiver'
    : !canEnable
      ? 'Activez d’abord le fournisseur'
      : 'Activer'

  const deleteTitle = isDefault
    ? 'Retirez le statut défaut avant de supprimer'
    : 'Supprimer ce modèle'

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

  return (
    <div className="row-actions-wrap" ref={rootRef}>
      <div
        className="row-actions row-actions--toolbar"
        role="toolbar"
        aria-label={`Actions pour ${label}`}
      >
        <button
          type="button"
          className="icon-btn icon-btn--muted"
          title="Renommer"
          aria-label={`Renommer ${label}`}
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
            aria-label="Définir par défaut"
            onClick={() => onSetDefault()}
          >
            <Star
              size={ICON_SIZE}
              aria-hidden="true"
              fill={isDefault ? 'currentColor' : 'none'}
            />
          </button>
          {isDefault && <span className="row-actions__default-label">Défaut</span>}
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
          disabled={isDefault}
          title={deleteTitle}
          aria-label="Supprimer"
          onClick={() => onDelete()}
        >
          <Trash2 size={ICON_SIZE} aria-hidden="true" />
        </button>
      </div>

      <div className="row-actions row-actions--menu">
        <button
          type="button"
          className="icon-btn icon-btn--muted"
          title="Actions"
          aria-label={`Menu actions pour ${label}`}
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
              Renommer
            </button>
            <button
              type="button"
              className="row-actions__dropdown-item"
              role="menuitem"
              disabled={isDefault || !model.enabled || !providerEnabled}
              onClick={() => runAction(onSetDefault)}
            >
              <Star size={ICON_SIZE} aria-hidden="true" />
              Définir par défaut
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
              {model.enabled ? 'Désactiver' : 'Activer'}
            </button>
            <button
              type="button"
              className="row-actions__dropdown-item row-actions__dropdown-item--danger"
              role="menuitem"
              disabled={isDefault}
              onClick={() => runAction(onDelete)}
            >
              <Trash2 size={ICON_SIZE} aria-hidden="true" />
              Supprimer
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
