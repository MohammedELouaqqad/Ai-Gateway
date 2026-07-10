import { useCallback, useEffect, useRef, useState } from 'react'
import { Pencil, Trash2 } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Badge } from '../components/Badge'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { sendChatMessage } from '../api/chat'
import {
  createConversation,
  deleteConversation,
  fetchConversations,
  fetchMessages,
  renameConversation,
} from '../api/conversations'
import { fetchModels } from '../api/models'
import { fetchPromptTemplates } from '../api/promptTemplates'
import type { ApiConversation, ApiMessage, ApiModel, ApiPromptTemplate } from '../api/types'
import { pages } from '../i18n/fr'
import {
  applyPromptTemplate,
  CONTENT_LIMITS,
  formatCharCount,
} from '../utils/contentLimits'
import { formatShortDate, formatTime, isModelDefault } from '../utils/mappers'

export function ChatPage() {
  const messagesRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const [conversations, setConversations] = useState<ApiConversation[]>([])
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null)
  const [messages, setMessages] = useState<ApiMessage[]>([])
  const [models, setModels] = useState<ApiModel[]>([])
  const [templates, setTemplates] = useState<ApiPromptTemplate[]>([])
  const [selectedModel, setSelectedModel] = useState('')
  const [selectedTemplateId, setSelectedTemplateId] = useState('')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const activeConversation = conversations.find((c) => c.id === activeConversationId)
  const activeModels = models.filter((m) => m.enabled && m.provider?.enabled)
  const selectedModelInfo = activeModels.find((m) => m.name === selectedModel) ?? models.find((m) => m.name === selectedModel)
  const selectedModelLabel =
    selectedModelInfo?.displayName
    ?? selectedModelInfo?.name
    ?? selectedModel
  const headerModelLabel =
    selectedModelLabel
    || activeConversation?.model?.displayName
    || activeConversation?.model?.name
    || ''
  const inputOverLimit = input.length > CONTENT_LIMITS.CHAT_MESSAGE_MAX
  const inputNearLimit = input.length >= CONTENT_LIMITS.CHAT_MESSAGE_WARN
  const canSend = !loading && !sending && !!input.trim() && !!selectedModel && !inputOverLimit
  const canApplyTemplate = !sending && !!selectedTemplateId

  function assistantModelLabel(message: ApiMessage): string | null {
    if (message.role !== 'ASSISTANT') return null
    // Priorité : modèle réellement utilisé pour CE message
    if (message.modelDisplayName || message.modelName) {
      return message.modelDisplayName || message.modelName || null
    }
    // Fallback historique (messages créés avant le stockage par message)
    return (
      activeConversation?.model?.displayName
      || activeConversation?.model?.name
      || null
    )
  }

  const scrollToBottom = useCallback(() => {
    const container = messagesRef.current
    if (!container) return
    requestAnimationFrame(() => {
      const last = container.lastElementChild as HTMLElement | null
      if (last) {
        last.scrollIntoView({ block: 'end', behavior: 'smooth' })
      } else {
        container.scrollTop = container.scrollHeight
      }
    })
  }, [])

  const loadConversations = useCallback(async () => {
    const data = await fetchConversations()
    const sorted = [...data].sort((a, b) => {
      const aTime = new Date(a.updatedAt ?? a.createdAt ?? 0).getTime()
      const bTime = new Date(b.updatedAt ?? b.createdAt ?? 0).getTime()
      return bTime - aTime
    })
    setConversations(sorted)
    return sorted
  }, [])

  const loadMessages = useCallback(async (conversationId: number) => {
    const data = await fetchMessages(conversationId)
    setMessages(data)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function init() {
      setLoading(true)
      setError(null)
      try {
        const [convData, modelData, templateData] = await Promise.all([
          fetchConversations(),
          fetchModels(),
          fetchPromptTemplates().catch(() => [] as ApiPromptTemplate[]),
        ])
        if (cancelled) return

        const sortedConversations = [...convData].sort((a, b) => {
          const aTime = new Date(a.updatedAt ?? a.createdAt ?? 0).getTime()
          const bTime = new Date(b.updatedAt ?? b.createdAt ?? 0).getTime()
          return bTime - aTime
        })
        setConversations(sortedConversations)
        setModels(modelData)
        setTemplates(templateData)

        const usable = modelData.filter((m) => m.enabled && m.provider?.enabled)
        const defaultM = usable.find((m) => isModelDefault(m))
        setSelectedModel(defaultM?.name ?? usable[0]?.name ?? '')

        if (sortedConversations.length > 0) {
          const firstId = sortedConversations[0].id
          setActiveConversationId(firstId)
          const msgData = await fetchMessages(firstId)
          if (!cancelled) setMessages(msgData)
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Impossible de charger les données')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void init()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, sending, scrollToBottom])

  async function handleSelectConversation(id: number) {
    setActiveConversationId(id)
    setError(null)
    try {
      await loadMessages(id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur chargement messages')
    }
  }

  async function handleNewConversation() {
    setError(null)
    try {
      const created = await createConversation()
      const updated = await loadConversations()
      setActiveConversationId(created.id)
      setMessages([])
      if (updated.length === 0) {
        setConversations([created])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur création conversation')
    }
  }

  async function handleDeleteConversation(id: number) {
    if (!window.confirm('Supprimer cette conversation ?')) return
    setError(null)
    try {
      await deleteConversation(id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur suppression')
    }
    try {
      const updated = await loadConversations()
      if (updated.length === 0) {
        setActiveConversationId(null)
        setMessages([])
      } else if (activeConversationId === id || !updated.some((c) => c.id === activeConversationId)) {
        setActiveConversationId(updated[0].id)
        await loadMessages(updated[0].id)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur rechargement')
    }
  }

  async function handleRenameConversation(id: number, currentTitle: string) {
    const title = window.prompt('Nouveau titre', currentTitle)
    if (!title?.trim()) return
    setError(null)
    try {
      await renameConversation(id, { title: title.trim() })
      await loadConversations()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur renommage')
      await loadConversations().catch(() => {})
    }
  }

  async function handleSend() {
    const content = input.trim()
    if (!content || sending || !selectedModel) return

    if (content.length > CONTENT_LIMITS.CHAT_MESSAGE_MAX) {
      setError(
        `Message trop long (${content.length.toLocaleString('fr-FR')} caractères, max ${CONTENT_LIMITS.CHAT_MESSAGE_MAX.toLocaleString('fr-FR')}).`,
      )
      return
    }

    setSending(true)
    setError(null)
    setInput('')

    const optimisticUser: ApiMessage = {
      id: Date.now(),
      role: 'USER',
      content,
    }
    setMessages((prev) => [...prev, optimisticUser])

    const controller = new AbortController()
    abortRef.current = controller

    try {
      const response = await sendChatMessage({
        conversationId: activeConversationId,
        content,
        model: selectedModel,
        stream: false,
      }, controller.signal)

      if (!activeConversationId) {
        const updated = await loadConversations()
        if (updated.length > 0) {
          setActiveConversationId(updated[0].id)
          await loadMessages(updated[0].id)
        }
      } else {
        await loadMessages(activeConversationId)
        await loadConversations()
      }

      if (!response.content) {
        setMessages((prev) => [
          ...prev.filter((m) => m.id !== optimisticUser.id),
          optimisticUser,
        ])
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        setMessages((prev) => prev.filter((m) => m.id !== optimisticUser.id))
        setInput(content)
        return
      }
      setMessages((prev) => prev.filter((m) => m.id !== optimisticUser.id))
      setInput(content)
      setError(e instanceof Error ? e.message : 'Erreur envoi message')
    } finally {
      abortRef.current = null
      setSending(false)
    }
  }

  function handleStop() {
    abortRef.current?.abort()
  }

  function handleApplyTemplate() {
    if (!selectedTemplateId) return
    const template = templates.find((t) => String(t.id) === selectedTemplateId)
    if (!template) return
    setInput((prev) => {
      const next = applyPromptTemplate(template.content, prev)
      if (next.length > CONTENT_LIMITS.CHAT_MESSAGE_MAX) {
        setError(
          `Après insertion du template, le message dépasse ${CONTENT_LIMITS.CHAT_MESSAGE_MAX.toLocaleString('fr-FR')} caractères. Raccourcissez le texte.`,
        )
        return next.slice(0, CONTENT_LIMITS.CHAT_MESSAGE_MAX)
      }
      setError(null)
      return next
    })
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault()
      void handleSend()
    }
  }

  return (
    <AppLayout breadcrumbs={[{ label: 'AI Gateway' }, { label: pages.chat.title }]} variant="chat">
      <ApiStateBanner loading={loading} error={error} />

      <div className="chat-shell" aria-label="Interface de chat">
        <aside className="chat-shell__sidebar" aria-label="Conversations">
          <div className="chat-shell__sidebar-header">
            <button
              type="button"
              className="btn btn--primary chat-shell__new-btn"
              onClick={() => void handleNewConversation()}
              disabled={loading}
            >
              + Nouvelle conversation
            </button>
          </div>
          <ul className="chat-conv-list" role="list">
            {conversations.map((c) => (
              <li key={c.id} className="chat-conv-list__item">
                <div
                  className={`chat-conv-row${c.id === activeConversationId ? ' chat-conv-row--active' : ''}`}
                >
                  <button
                    type="button"
                    className="chat-conv-item"
                    aria-current={c.id === activeConversationId ? 'true' : undefined}
                    onClick={() => void handleSelectConversation(c.id)}
                  >
                    <span className="chat-conv-item__title">{c.title}</span>
                    <span className="chat-conv-item__date mono">{formatShortDate(c.updatedAt ?? c.createdAt)}</span>
                  </button>
                  <div className="chat-conv-row__actions">
                    <button
                      type="button"
                      className="chat-conv-action"
                      aria-label={`Renommer ${c.title}`}
                      title="Renommer"
                      onClick={(e) => {
                        e.stopPropagation()
                        void handleRenameConversation(c.id, c.title)
                      }}
                    >
                      <Pencil size={16} strokeWidth={1.75} aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      className="chat-conv-action chat-conv-action--danger"
                      aria-label={`Supprimer ${c.title}`}
                      title="Supprimer"
                      onClick={(e) => {
                        e.stopPropagation()
                        void handleDeleteConversation(c.id)
                      }}
                    >
                      <Trash2 size={16} strokeWidth={1.75} aria-hidden="true" />
                    </button>
                  </div>
                </div>
              </li>
            ))}
            {!loading && conversations.length === 0 && (
              <li className="chat-conv-list__empty">Aucune conversation</li>
            )}
          </ul>
        </aside>

        <div className="chat-shell__main">
          <header className="chat-toolbar">
            <div className="chat-toolbar__info">
              <h1 className="chat-toolbar__title">
                {activeConversation?.title ?? 'Nouvelle conversation'}
              </h1>
              <p className="chat-toolbar__meta">
                {messages.length} message{messages.length !== 1 ? 's' : ''}
                {headerModelLabel ? ` · ${headerModelLabel}` : ''}
              </p>
            </div>
            <div className="chat-toolbar__actions">
              <label className="visually-hidden" htmlFor="chat-model">Modèle</label>
              <select
                className="form-select chat-toolbar__model"
                id="chat-model"
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
                disabled={activeModels.length === 0}
              >
                {activeModels.map((m) => (
                  <option key={m.id} value={m.name}>
                    {m.displayName || m.name}
                    {isModelDefault(m) ? ' (défaut)' : ''}
                  </option>
                ))}
              </select>
              <Badge variant={error ? 'error' : 'success'} dot>
                {error ? 'Erreur' : 'Connecté'}
              </Badge>
            </div>
          </header>

          <div className="chat-messages" ref={messagesRef} role="log" aria-label="Messages">
            {messages.map((m) => {
              const isUser = m.role === 'USER'
              const isSystem = m.role === 'SYSTEM'
              const roleClass = isUser ? 'user' : isSystem ? 'system' : 'assistant'
              const label = isUser ? 'Vous' : isSystem ? 'Système' : 'Assistant'
              const modelLabel = assistantModelLabel(m)
              return (
                <article
                  key={m.id}
                  className={`chat-message chat-message--${roleClass}`}
                >
                  <div className="chat-message__avatar" aria-hidden="true">
                    {isUser ? 'V' : isSystem ? 'S' : 'AI'}
                  </div>
                  <div className="chat-message__body">
                    <header className="chat-message__meta">
                      <span className="chat-message__author">{label}</span>
                      {modelLabel && (
                        <>
                          <span className="chat-message__sep" aria-hidden="true">·</span>
                          <span className="chat-message__model">{modelLabel}</span>
                        </>
                      )}
                      {m.createdAt && (
                        <>
                          <span className="chat-message__sep" aria-hidden="true">·</span>
                          <time className="chat-message__time mono" dateTime={m.createdAt}>
                            {formatTime(m.createdAt)}
                          </time>
                        </>
                      )}
                    </header>
                    <div className="chat-message__bubble">{m.content}</div>
                  </div>
                </article>
              )
            })}

            {sending && (
              <article className="chat-message chat-message--assistant chat-message--streaming">
                <div className="chat-message__avatar" aria-hidden="true">AI</div>
                <div className="chat-message__body">
                  <header className="chat-message__meta">
                    <span className="chat-message__author">Assistant</span>
                    {selectedModelLabel && (
                      <>
                        <span className="chat-message__sep" aria-hidden="true">·</span>
                        <span className="chat-message__model">{selectedModelLabel}</span>
                      </>
                    )}
                    <span className="chat-message__sep" aria-hidden="true">·</span>
                    <span className="chat-message__status">En train d’écrire…</span>
                  </header>
                  <div className="chat-message__bubble chat-message__bubble--typing">
                    <p className="chat-typing" aria-live="polite">
                      <span className="chat-typing__dots" aria-hidden="true">
                        <span /><span /><span />
                      </span>
                      Génération en cours…
                    </p>
                  </div>
                </div>
              </article>
            )}

            {!loading && messages.length === 0 && !sending && (
              <p className="chat-messages__empty">Envoyez un message pour démarrer.</p>
            )}
          </div>

          <footer className="chat-composer">
            {templates.length > 0 && (
              <div className="chat-composer__toolbar">
                <div className="chat-composer__field chat-composer__field--template">
                  <label className="chat-composer__label" htmlFor="chat-template">Template</label>
                  <div className="chat-composer__template-row">
                    <select
                      id="chat-template"
                      className="form-select chat-composer__select"
                      value={selectedTemplateId}
                      disabled={sending}
                      onChange={(e) => setSelectedTemplateId(e.target.value)}
                    >
                      <option value="">Choisir un template…</option>
                      {templates.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      onClick={handleApplyTemplate}
                      disabled={!canApplyTemplate}
                      title="Injecte le template dans la zone de saisie (remplace {{content}})"
                    >
                      Appliquer
                    </button>
                  </div>
                </div>
              </div>
            )}
            <form
              className="chat-composer__row"
              onSubmit={(e) => {
                e.preventDefault()
                void handleSend()
              }}
            >
              <label className="visually-hidden" htmlFor="chat-input">Message</label>
              <textarea
                className="form-textarea chat-composer__input"
                id="chat-input"
                rows={input.length > 400 ? 6 : 2}
                placeholder="Saisissez votre message…"
                value={input}
                onChange={(e) => setInput(e.target.value.slice(0, CONTENT_LIMITS.CHAT_MESSAGE_MAX))}
                onKeyDown={handleKeyDown}
                disabled={sending}
                maxLength={CONTENT_LIMITS.CHAT_MESSAGE_MAX}
              />
              <div className="chat-composer__actions">
                <button
                  type="button"
                  className="chat-btn chat-btn--stop"
                  aria-label="Arrêter la génération"
                  onClick={handleStop}
                  disabled={!sending}
                >
                  Stop
                </button>
                <button
                  type="submit"
                  className="chat-btn chat-btn--primary"
                  aria-label="Envoyer le message"
                  disabled={!canSend}
                  title={
                    !selectedModel || activeModels.length === 0
                      ? 'Activez un fournisseur et un modèle'
                      : inputOverLimit
                        ? 'Message trop long'
                        : !input.trim()
                          ? 'Saisissez un message'
                          : 'Envoyer'
                  }
                >
                  Envoyer
                </button>
              </div>
            </form>
            <p
              className={
                inputOverLimit
                  ? 'chat-composer__hint chat-composer__hint--danger'
                  : inputNearLimit
                    ? 'chat-composer__hint chat-composer__hint--warn'
                    : 'chat-composer__hint'
              }
            >
              {sending
                ? 'Génération en cours…'
                : activeModels.length === 0
                  ? 'Aucun modèle utilisable — activez un fournisseur puis un modèle'
                  : `${formatCharCount(input.length, CONTENT_LIMITS.CHAT_MESSAGE_MAX)} · Ctrl+Entrée pour envoyer`}
            </p>
          </footer>
        </div>
      </div>
    </AppLayout>
  )
}
