import { useCallback, useEffect, useRef, useState } from 'react'
import { FileText, LayoutTemplate, Paperclip, Pencil, Plus, RefreshCw, ThumbsDown, ThumbsUp, Trash2, X } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { ApiStateBanner } from '../components/ApiStateBanner'
import { AttachmentThumbnail } from '../components/AttachmentThumbnail'
import { Badge } from '../components/Badge'
import { sendChatStream } from '../api/chat'
import {
  createConversation,
  deleteConversation,
  fetchConversations,
  fetchMessages,
  renameConversation,
} from '../api/conversations'
import { clearMessageFeedback, regenerateMessage, submitMessageFeedback } from '../api/feedback'
import { uploadFile } from '../api/files'
import { fetchModels } from '../api/models'
import { fetchPromptTemplates } from '../api/promptTemplates'
import {
  attachRagDocument,
  deleteRagDocument,
  detachRagDocument,
  fetchConversationRagDocuments,
  fetchRagDocuments,
  uploadRagDocument,
} from '../api/rag'
import type {
  ApiAttachment,
  ApiConversation,
  ApiMessage,
  ApiModel,
  ApiPromptTemplate,
  ApiRagDocument,
  FeedbackRating,
} from '../api/types'
import { useI18n } from '../i18n'
import {
  applyPromptTemplate,
  CONTENT_LIMITS,
  formatTokenCount,
} from '../utils/contentLimits'
import { formatShortDate, formatTime, isModelDefault } from '../utils/mappers'

/** Mirrors backend app.uploads defaults — the server remains authoritative (this is purely a
 *  fast client-side rejection before spending a network round-trip on an obviously bad file). */
const ATTACH_MAX_SIZE_BYTES = 10_000_000
const ATTACH_ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp']

/** Mirrors backend app.rag defaults — same "fast client rejection, server stays authoritative"
 *  rationale as the image constants above. */
const RAG_MAX_SIZE_BYTES = 20_000_000

export function ChatPage() {
  const { pages, common } = useI18n()
  const chat = pages.chat
  const messagesRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
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
  const [attachment, setAttachment] = useState<ApiAttachment | null>(null)
  const [attachmentPreviewUrl, setAttachmentPreviewUrl] = useState<string | null>(null)
  const [ragDocuments, setRagDocuments] = useState<ApiRagDocument[]>([])
  const [conversationRagDocuments, setConversationRagDocuments] = useState<ApiRagDocument[]>([])
  const [ragDocUploading, setRagDocUploading] = useState(false)
  const [ragDocToAttach, setRagDocToAttach] = useState('')
  // Collapsed by default (like the template row, which only renders when there's something
  // to show) — the panel takes real, permanent composer height while open, and staying open
  // unconditionally is exactly what crushed .chat-messages before. Auto-opens once per
  // conversation switch when that conversation already has documents attached, so existing
  // context isn't hidden by default — see the sync effect below.
  const [documentsPanelOpen, setDocumentsPanelOpen] = useState(false)
  const ragDocInputRef = useRef<HTMLInputElement>(null)
  const [attachUploading, setAttachUploading] = useState(false)
  // Same "collapsed unless there's a reason to show it" rationale as documentsPanelOpen above —
  // the template row used to render unconditionally whenever templates existed, which is the
  // same permanent-height mistake that crushed .chat-messages before. Now only opens via the
  // unified "+" composer menu.
  const [templatesPanelOpen, setTemplatesPanelOpen] = useState(false)
  const [composerMenuOpen, setComposerMenuOpen] = useState(false)
  const composerMenuRef = useRef<HTMLDivElement>(null)

  const activeConversation = conversations.find((c) => c.id === activeConversationId)
  const activeModels = models.filter((m) => m.enabled && m.provider?.enabled)
  const selectableModels = activeModels.filter((m) => m.available !== false)
  const selectedModelInfo = activeModels.find((m) => m.name === selectedModel) ?? models.find((m) => m.name === selectedModel)
  // No "Auto" option anymore — the default model is already pre-selected by name on load
  // (see the init effect below), so an empty selectedModel only ever means "still loading" /
  // "no usable model exists yet", never a deliberate user choice.
  const selectedModelLabel = selectedModelInfo?.displayName ?? selectedModelInfo?.name ?? selectedModel
  const selectedAvailable = selectedModelInfo?.available !== false
  const selectedSupportsStreaming =
    selectedAvailable
    && selectedModelInfo?.supportsStreaming !== false
    && (selectedModelInfo?.supportsStreaming === true
      || selectedModelInfo?.provider?.type === 'OLLAMA'
      || selectedModelInfo?.provider?.type === 'OPENAI'
      || selectedModelInfo?.provider?.type === 'AZURE_OPENAI')
  const headerModelLabel =
    selectedModelLabel
    || activeConversation?.model?.displayName
    || activeConversation?.model?.name
    || ''
  const inputOverLimit = input.length > CONTENT_LIMITS.CHAT_MESSAGE_MAX
  const inputNearLimit = input.length >= CONTENT_LIMITS.CHAT_MESSAGE_WARN
  const selectedModelVisionCapable = selectedModelInfo?.visionCapable === true
  const canAttach = !sending && !attachUploading && selectedModelVisionCapable
  const canSend =
    !loading
    && !sending
    && !attachUploading
    && !!input.trim()
    && !!selectedModel
    && selectedAvailable
    && selectableModels.length > 0
    && !inputOverLimit
    && (!attachment || selectedModelVisionCapable)
  const canApplyTemplate = !sending && !!selectedTemplateId
  const unattachedRagDocuments = ragDocuments.filter(
    (d) => !conversationRagDocuments.some((cd) => cd.id === d.id),
  )

  async function handleFeedback(messageId: number, rating: FeedbackRating) {
    const current = messages.find((m) => m.id === messageId)?.myFeedback ?? null
    const next: FeedbackRating | null = current === rating ? null : rating
    setMessages((prev) =>
      prev.map((m) => (m.id === messageId ? { ...m, myFeedback: next } : m)),
    )
    try {
      if (next == null) {
        await clearMessageFeedback(messageId)
      } else {
        await submitMessageFeedback(messageId, next)
      }
    } catch {
      setMessages((prev) =>
        prev.map((m) => (m.id === messageId ? { ...m, myFeedback: current } : m)),
      )
      setError(chat.feedbackError)
    }
  }

  async function handleRegenerate(messageId: number) {
    if (sending) return
    setSending(true)
    setError(null)
    try {
      await regenerateMessage(messageId)
      if (activeConversationId != null) {
        const refreshed = await fetchMessages(activeConversationId)
        setMessages(refreshed)
      }
    } catch {
      setError(chat.regenerateError)
    } finally {
      setSending(false)
    }
  }

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
      // Deliberately NOT element.scrollIntoView() — on some browsers it walks up through
      // overflow:hidden ancestors (.chat-shell, .app-shell — the whole fixed app chrome is
      // built on overflow:hidden, see style.css) and can end up scrolling the page itself
      // instead of just this panel, especially once the composer is tall enough (RAG
      // documents section) that the "bring fully into view" calculation doesn't resolve
      // within .chat-messages alone. Setting scrollTop directly can only ever move this one
      // container — there is no ancestor bleed-through possible.
      container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' })
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

  const loadConversationRagDocuments = useCallback(async (conversationId: number) => {
    const data = await fetchConversationRagDocuments(conversationId)
    setConversationRagDocuments(data)
    return data
  }, [])

  useEffect(() => {
    let cancelled = false

    async function init() {
      setLoading(true)
      setError(null)
      try {
        const [convData, modelData, templateData, ragDocData] = await Promise.all([
          fetchConversations(),
          fetchModels(),
          fetchPromptTemplates().catch(() => [] as ApiPromptTemplate[]),
          fetchRagDocuments().catch(() => [] as ApiRagDocument[]),
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
        setRagDocuments(ragDocData)

        const usable = modelData.filter(
          (m) => m.enabled && m.provider?.enabled && m.available !== false,
        )
        const defaultM = usable.find((m) => isModelDefault(m))
        setSelectedModel(defaultM?.name ?? usable[0]?.name ?? '')

        if (sortedConversations.length > 0) {
          const firstId = sortedConversations[0].id
          setActiveConversationId(firstId)
          const msgData = await fetchMessages(firstId)
          if (!cancelled) setMessages(msgData)
          const convRagDocs = await fetchConversationRagDocuments(firstId).catch(() => [] as ApiRagDocument[])
          if (!cancelled) {
            setConversationRagDocuments(convRagDocs)
            // Auto-open only here and in handleSelectConversation (a real conversation
            // switch) — never from the attach/detach/polling refreshes below, or closing
            // the panel while it polls would just snap it back open on the user.
            setDocumentsPanelOpen(convRagDocs.length > 0)
          }
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
      // Same "real conversation switch" auto-open rule as the init effect — see there.
      const convRagDocs = await loadConversationRagDocuments(id).catch(() => [] as ApiRagDocument[])
      setDocumentsPanelOpen(convRagDocs.length > 0)
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
      setConversationRagDocuments([])
      setDocumentsPanelOpen(false)
      if (updated.length === 0) {
        setConversations([created])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erreur création conversation')
    }
  }

  async function handleDeleteConversation(id: number) {
    if (!window.confirm(chat.deleteConfirm)) return
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
    const title = window.prompt(chat.renamePrompt, currentTitle)
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
    if (attachment && !selectedModelVisionCapable) {
      setError(chat.sendNeedVisionModel)
      return
    }

    if (content.length > CONTENT_LIMITS.CHAT_MESSAGE_MAX) {
      setError(
        `Message trop long (${content.length.toLocaleString('fr-FR')} caractères, max ${CONTENT_LIMITS.CHAT_MESSAGE_MAX.toLocaleString('fr-FR')}).`,
      )
      return
    }

    const sentAttachmentId = attachment?.id ?? null
    setSending(true)
    setError(null)
    setInput('')
    clearAttachment()

    const optimisticUser: ApiMessage = {
      id: Date.now(),
      role: 'USER',
      content,
      attachmentId: sentAttachmentId,
    }
    const optimisticAssistant: ApiMessage = {
      id: Date.now() + 1,
      role: 'ASSISTANT',
      content: '',
      modelDisplayName: selectedModelLabel || null,
      modelName: selectedModel || null,
    }
    setMessages((prev) => [...prev, optimisticUser, optimisticAssistant])

    const controller = new AbortController()
    abortRef.current = controller
    let streamedAssistantText = ''

    try {
      await sendChatStream(
        {
          conversationId: activeConversationId,
          content,
          model: selectedModel || undefined,
          stream: true,
          attachmentId: sentAttachmentId,
        },
        {
          onChunk: (chunk) => {
            if (chunk.done) return
            if (!chunk.content) return
            streamedAssistantText += chunk.content
            setMessages((prev) =>
              prev.map((m) =>
                m.id === optimisticAssistant.id
                  ? { ...m, content: `${m.content}${chunk.content}` }
                  : m,
              ),
            )
          },
        },
        controller.signal,
      )

      // Stream OK : recharger depuis l’API sans effacer l’affichage en cas d’échec de reload
      try {
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
      } catch (reloadErr) {
        setError(
          reloadErr instanceof Error
            ? `Réponse reçue, mais rechargement impossible : ${reloadErr.message}`
            : 'Réponse reçue, mais rechargement impossible',
        )
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        setMessages((prev) =>
          prev.filter((m) => m.id !== optimisticUser.id && m.id !== optimisticAssistant.id),
        )
        setInput(content)
        return
      }
      // Si du texte a déjà streamé, on le garde visible (évite « flash puis disparition »)
      if (!streamedAssistantText) {
        setMessages((prev) =>
          prev.filter((m) => m.id !== optimisticUser.id && m.id !== optimisticAssistant.id),
        )
        setInput(content)
      } else {
        try {
          if (activeConversationId) {
            await loadMessages(activeConversationId)
          }
        } catch {
          /* garder le texte optimiste */
        }
      }
      setError(e instanceof Error ? e.message : 'Erreur envoi message')
    } finally {
      abortRef.current = null
      setSending(false)
    }
  }

  function handleStop() {
    abortRef.current?.abort()
  }

  function clearAttachment() {
    if (attachmentPreviewUrl) URL.revokeObjectURL(attachmentPreviewUrl)
    setAttachment(null)
    setAttachmentPreviewUrl(null)
  }

  function handleAttachClick() {
    fileInputRef.current?.click()
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = '' // allow re-selecting the same file later
    if (!file) return

    if (!ATTACH_ALLOWED_TYPES.includes(file.type)) {
      setError(chat.attachError)
      return
    }
    if (file.size > ATTACH_MAX_SIZE_BYTES) {
      setError(chat.attachError)
      return
    }

    clearAttachment()
    setError(null)
    setAttachUploading(true)
    const previewUrl = URL.createObjectURL(file)
    setAttachmentPreviewUrl(previewUrl)
    try {
      const uploaded = await uploadFile(file)
      setAttachment(uploaded)
    } catch (err) {
      URL.revokeObjectURL(previewUrl)
      setAttachmentPreviewUrl(null)
      setError(err instanceof Error ? err.message : chat.attachError)
    } finally {
      setAttachUploading(false)
    }
  }

  // Revokes the PREVIOUS object URL whenever it changes, and the last one on unmount — this
  // (not an empty dep array) is what avoids a stale-closure leak if the user navigates away
  // with a file attached but never sent.
  useEffect(() => {
    return () => {
      if (attachmentPreviewUrl) URL.revokeObjectURL(attachmentPreviewUrl)
    }
  }, [attachmentPreviewUrl])

  // Closes the "+" composer popover on outside click or Escape — standard popover behavior,
  // only wired up while the menu is actually open (no listeners attached otherwise).
  useEffect(() => {
    if (!composerMenuOpen) return
    function handlePointerDown(e: MouseEvent) {
      if (composerMenuRef.current && !composerMenuRef.current.contains(e.target as Node)) {
        setComposerMenuOpen(false)
      }
    }
    function handleKeyDownGlobal(e: KeyboardEvent) {
      if (e.key === 'Escape') setComposerMenuOpen(false)
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDownGlobal)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDownGlobal)
    }
  }, [composerMenuOpen])

  function handleRagDocUploadClick() {
    ragDocInputRef.current?.click()
  }

  async function handleRagDocFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return

    if (file.type !== 'application/pdf') {
      setError(chat.documentError)
      return
    }
    if (file.size > RAG_MAX_SIZE_BYTES) {
      setError(chat.documentError)
      return
    }

    setError(null)
    setRagDocUploading(true)
    try {
      const uploaded = await uploadRagDocument(file)
      setRagDocuments((prev) => [uploaded, ...prev])
      if (activeConversationId) {
        await handleRagDocAttach(uploaded.id)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : chat.documentError)
    } finally {
      setRagDocUploading(false)
    }
  }

  async function handleRagDocAttach(documentId: number) {
    if (!activeConversationId) return
    setError(null)
    try {
      await attachRagDocument(activeConversationId, documentId)
      await loadConversationRagDocuments(activeConversationId)
      setRagDocToAttach('')
    } catch (err) {
      setError(err instanceof Error ? err.message : chat.documentError)
    }
  }

  async function handleRagDocDetach(documentId: number) {
    if (!activeConversationId) return
    setError(null)
    try {
      await detachRagDocument(activeConversationId, documentId)
      setConversationRagDocuments((prev) => prev.filter((d) => d.id !== documentId))
    } catch (err) {
      setError(err instanceof Error ? err.message : chat.documentError)
    }
  }

  async function handleRagDocDelete(documentId: number, filename: string) {
    if (!window.confirm(chat.documentDeleteConfirm.replace('{name}', filename))) return
    setError(null)
    try {
      await deleteRagDocument(documentId)
      setRagDocuments((prev) => prev.filter((d) => d.id !== documentId))
      setConversationRagDocuments((prev) => prev.filter((d) => d.id !== documentId))
      setRagDocToAttach('')
    } catch (err) {
      setError(err instanceof Error ? err.message : chat.documentError)
    }
  }

  // Poll while any attached document is still being ingested, so its status badge (and the
  // moment it becomes usable for retrieval) updates without a manual refresh.
  useEffect(() => {
    if (!activeConversationId) return
    const hasPending = conversationRagDocuments.some(
      (d) => d.status === 'PENDING' || d.status === 'PROCESSING',
    )
    if (!hasPending) return
    const interval = window.setInterval(() => {
      void loadConversationRagDocuments(activeConversationId)
    }, 5000)
    return () => window.clearInterval(interval)
  }, [activeConversationId, conversationRagDocuments, loadConversationRagDocuments])

  function ragDocStatusLabel(status: ApiRagDocument['status']): string {
    switch (status) {
      case 'READY': return chat.documentStatusReady
      case 'PROCESSING': return chat.documentStatusProcessing
      case 'FAILED': return chat.documentStatusFailed
      default: return chat.documentStatusPending
    }
  }

  function ragDocStatusVariant(status: ApiRagDocument['status']): 'success' | 'error' | 'secondary' {
    if (status === 'READY') return 'success'
    if (status === 'FAILED') return 'error'
    return 'secondary'
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

      <div className="chat-shell" aria-label={chat.ariaChat}>
        <aside className="chat-shell__sidebar" aria-label={chat.ariaConversations}>
          <div className="chat-shell__sidebar-header">
            <button
              type="button"
              className="btn btn--primary chat-shell__new-btn"
              onClick={() => void handleNewConversation()}
              disabled={loading}
            >
              {chat.newConversation}
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
                      aria-label={`${common.rename} ${c.title}`}
                      title={common.rename}
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
                      aria-label={`${common.delete} ${c.title}`}
                      title={common.delete}
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
              <li className="chat-conv-list__empty">{chat.noConversations}</li>
            )}
          </ul>
        </aside>

        <div className="chat-shell__main">
          <header className="chat-toolbar">
            <div className="chat-toolbar__info">
              <h1 className="chat-toolbar__title">
                {activeConversation?.title ?? chat.newConversationTitle}
              </h1>
              <p className="chat-toolbar__meta">
                {(messages.length === 1 ? chat.messagesCount : chat.messagesCountPlural).replace(
                  '{n}',
                  String(messages.length),
                )}
                {headerModelLabel ? ` · ${headerModelLabel}` : ''}
                {selectedModelInfo
                  ? selectedSupportsStreaming
                    ? ' · Stream'
                    : ' · Sync'
                  : ''}
              </p>
            </div>
            <div className="chat-toolbar__actions">
              <label className="visually-hidden" htmlFor="chat-model">{chat.model}</label>
              <select
                className="form-select chat-toolbar__model"
                id="chat-model"
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
                disabled={selectableModels.length === 0}
              >
                {activeModels.map((m) => {
                  const unavailable = m.available === false
                  return (
                    <option key={m.id} value={m.name} disabled={unavailable}>
                      {m.displayName || m.name}
                      {isModelDefault(m) ? common.defaultSuffix : ''}
                      {unavailable ? common.unavailable : ''}
                    </option>
                  )
                })}
              </select>
            </div>
          </header>

          <div className="chat-messages" ref={messagesRef} role="log" aria-label={chat.ariaMessages}>
            {messages.map((m, index) => {
              const isUser = m.role === 'USER'
              const isSystem = m.role === 'SYSTEM'
              const roleClass = isUser ? 'user' : isSystem ? 'system' : 'assistant'
              const label = isUser ? chat.you : isSystem ? chat.system : chat.assistant
              const modelLabel = assistantModelLabel(m)
              const isStreamingPlaceholder =
                sending && !isUser && !isSystem && !m.content && m === messages[messages.length - 1]
              // Once the first token has arrived, the pulsing-dots placeholder above no longer
              // applies — this instead tracks "still actively streaming" so the bubble can show
              // a blinking cursor after the partial text, and clears the moment sending stops.
              const isStreamingActive =
                sending && !isUser && !isSystem && !!m.content && m === messages[messages.length - 1]
              // Semantic 2-level spacing (see .chat-message margin rules): each user message
              // starts a new exchange, so it — and only it — gets the larger inter-pair gap.
              // Purely a rendering/grouping decision over the existing messages array, no data
              // or request changes.
              const isGroupStart = index === 0 || isUser
              return (
                <article
                  key={m.id}
                  className={`chat-message chat-message--${roleClass}${isStreamingPlaceholder ? ' chat-message--streaming' : ''}${isGroupStart ? ' chat-message--group-start' : ''}`}
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
                      {!isUser && !isSystem && typeof m.ragChunksUsed === 'number' && m.ragChunksUsed > 0 && (
                        <>
                          <span className="chat-message__sep" aria-hidden="true">·</span>
                          <span
                            className="chat-message__rag-badge"
                            title={chat.documentRagUsedTitle.replace('{count}', String(m.ragChunksUsed))}
                          >
                            <FileText size={12} aria-hidden="true" />
                            {chat.documentRagUsed.replace('{count}', String(m.ragChunksUsed))}
                          </span>
                        </>
                      )}
                      {isStreamingPlaceholder ? (
                        <>
                          <span className="chat-message__sep" aria-hidden="true">·</span>
                          <span className="chat-message__status">{chat.typing}</span>
                        </>
                      ) : m.createdAt ? (
                        <>
                          <span className="chat-message__sep" aria-hidden="true">·</span>
                          <time className="chat-message__time mono" dateTime={m.createdAt}>
                            {formatTime(m.createdAt)}
                          </time>
                        </>
                      ) : null}
                    </header>
                    {typeof m.attachmentId === 'number' && (
                      <div className="chat-message__attachment">
                        <AttachmentThumbnail attachmentId={m.attachmentId} alt={chat.attach} />
                      </div>
                    )}
                    <div className={`chat-message__bubble${isStreamingPlaceholder ? ' chat-message__bubble--typing' : ''}`}>
                      {isStreamingPlaceholder ? (
                  <p className="chat-typing" aria-live="polite">
                    <span className="chat-typing__dots" aria-hidden="true">
                      <span /><span /><span />
                    </span>
                    {chat.generating}
                  </p>
                      ) : (
                        <>
                          {m.content}
                          {isStreamingActive && <span className="chat-cursor-blink" aria-hidden="true" />}
                        </>
                      )}
                </div>
                {!isUser && !isSystem && !isStreamingPlaceholder && 'myFeedback' in m && typeof m.id === 'number' && !m.regenerated && (
                  <div className="chat-message__feedback" role="group" aria-label={chat.feedbackUp}>
                    <button
                      type="button"
                      className={`chat-feedback-btn${m.myFeedback === 'UP' ? ' is-active is-up' : ''}`}
                      title={m.myFeedback === 'UP' ? chat.feedbackClear : chat.feedbackUp}
                      aria-pressed={m.myFeedback === 'UP'}
                      disabled={sending}
                      onClick={() => void handleFeedback(m.id, 'UP')}
                    >
                      <ThumbsUp size={14} aria-hidden="true" />
                      <span className="sr-only">{chat.feedbackUp}</span>
                    </button>
                    <button
                      type="button"
                      className={`chat-feedback-btn${m.myFeedback === 'DOWN' ? ' is-active is-down' : ''}`}
                      title={m.myFeedback === 'DOWN' ? chat.feedbackClear : chat.feedbackDown}
                      aria-pressed={m.myFeedback === 'DOWN'}
                      disabled={sending}
                      onClick={() => void handleFeedback(m.id, 'DOWN')}
                    >
                      <ThumbsDown size={14} aria-hidden="true" />
                      <span className="sr-only">{chat.feedbackDown}</span>
                    </button>
                    <button
                      type="button"
                      className="chat-feedback-btn"
                      title={chat.regenerateTitle}
                      disabled={sending}
                      onClick={() => void handleRegenerate(m.id)}
                    >
                      <RefreshCw size={14} aria-hidden="true" />
                      <span className="sr-only">{chat.regenerate}</span>
                    </button>
                  </div>
                )}
              </div>
            </article>
              )
            })}

            {!loading && messages.length === 0 && !sending && (
              <p className="chat-messages__empty">{chat.empty}</p>
            )}
          </div>

          <footer className="chat-composer">
            {templates.length > 0 && templatesPanelOpen && (
              <div className="chat-composer__toolbar">
                <div className="chat-composer__field chat-composer__field--template">
                  <label className="chat-composer__label" htmlFor="chat-template">{chat.template}</label>
                  <div className="chat-composer__template-row">
                    <select
                      id="chat-template"
                      className="form-select chat-composer__select"
                      value={selectedTemplateId}
                      disabled={sending}
                      onChange={(e) => setSelectedTemplateId(e.target.value)}
                    >
                      <option value="">{chat.chooseTemplate}</option>
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
                      title={chat.applyTitle}
                    >
                      {chat.apply}
                    </button>
                    <button
                      type="button"
                      className="chat-composer__panel-close"
                      aria-label={chat.panelClose}
                      title={chat.panelClose}
                      onClick={() => setTemplatesPanelOpen(false)}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                </div>
              </div>
            )}
            {documentsPanelOpen && (
            <div className="chat-composer__toolbar chat-composer__toolbar--documents" aria-label={chat.documentsAria}>
              <span className="chat-composer__label">{chat.documents}</span>
              {unattachedRagDocuments.length > 0 && (
                // Selecting attaches immediately — no separate "Attach" click to forget. That
                // two-step version is exactly what caused a real RAG-context bug: a user picked
                // a document here, never clicked Attach, and the model silently answered with
                // zero document context (see docs/rag-security-review.md-adjacent incident).
                // Permanent deletion moved to the attached chips below, since a document you can
                // still pick from this list is, by construction, not attached yet.
                <select
                  id="chat-rag-doc"
                  className="form-select chat-composer__select chat-composer__select--compact"
                  value={ragDocToAttach}
                  disabled={sending || ragDocUploading || !activeConversationId}
                  onChange={(e) => {
                    const id = e.target.value
                    if (id) void handleRagDocAttach(Number(id))
                  }}
                >
                  <option value="">{chat.documentChoose}</option>
                  {unattachedRagDocuments.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.filename}{d.status !== 'READY' ? ` — ${ragDocStatusLabel(d.status)}` : ''}
                    </option>
                  ))}
                </select>
              )}
              <input
                ref={ragDocInputRef}
                type="file"
                accept="application/pdf"
                className="visually-hidden"
                aria-hidden="true"
                tabIndex={-1}
                onChange={(e) => void handleRagDocFileChange(e)}
              />
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={handleRagDocUploadClick}
                disabled={ragDocUploading || sending}
                title={chat.documentUpload}
              >
                <FileText size={14} aria-hidden="true" /> {ragDocUploading ? chat.documentUploading : chat.documentUpload}
              </button>
              {conversationRagDocuments.length === 0 && ragDocuments.length === 0 && (
                <span className="chat-composer__hint" style={{ margin: 0 }}>{chat.documentNone}</span>
              )}
              {conversationRagDocuments.map((d) => (
                <div key={d.id} className="chat-attachment-chip">
                  <FileText size={16} aria-hidden="true" />
                  <span>{d.filename}</span>
                  <Badge variant={ragDocStatusVariant(d.status)}>{ragDocStatusLabel(d.status)}</Badge>
                  <button
                    type="button"
                    className="chat-attachment-chip__remove"
                    aria-label={chat.documentDetach}
                    title={chat.documentDetach}
                    onClick={() => void handleRagDocDetach(d.id)}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    className="chat-attachment-chip__remove"
                    aria-label={chat.documentDelete}
                    title={chat.documentDelete}
                    onClick={() => void handleRagDocDelete(d.id, d.filename)}
                  >
                    <Trash2 size={14} aria-hidden="true" />
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="chat-composer__panel-close"
                aria-label={chat.panelClose}
                title={chat.panelClose}
                onClick={() => setDocumentsPanelOpen(false)}
              >
                <X size={14} aria-hidden="true" />
              </button>
            </div>
            )}
            {(attachmentPreviewUrl || attachUploading) && (
              <div className="chat-attachment-chip">
                {attachmentPreviewUrl ? (
                  <img className="chat-attachment-thumb" src={attachmentPreviewUrl} alt={chat.attach} />
                ) : (
                  <div className="chat-attachment-thumb chat-attachment-thumb--loading" aria-hidden="true" />
                )}
                <span>{attachUploading ? chat.attachUploading : attachment?.filename}</span>
                <button
                  type="button"
                  className="chat-attachment-chip__remove"
                  aria-label={chat.attachRemove}
                  title={chat.attachRemove}
                  onClick={clearAttachment}
                  disabled={attachUploading}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            )}
            <form
              className="chat-composer__row"
              onSubmit={(e) => {
                e.preventDefault()
                void handleSend()
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept={ATTACH_ALLOWED_TYPES.join(',')}
                className="visually-hidden"
                aria-hidden="true"
                tabIndex={-1}
                onChange={(e) => void handleFileChange(e)}
              />
              <div className="chat-composer__plus-wrap" ref={composerMenuRef}>
                <button
                  type="button"
                  className="chat-composer__attach-btn"
                  aria-label={chat.composerMenuAria}
                  aria-haspopup="menu"
                  aria-expanded={composerMenuOpen}
                  title={chat.composerMenuAria}
                  onClick={() => setComposerMenuOpen((prev) => !prev)}
                >
                  <Plus size={18} aria-hidden="true" />
                  {conversationRagDocuments.length > 0 && (
                    <span className="chat-composer__attach-btn-badge">{conversationRagDocuments.length}</span>
                  )}
                </button>
                {composerMenuOpen && (
                  <div className="chat-composer__menu" role="menu" aria-label={chat.composerMenuAria}>
                    <button
                      type="button"
                      role="menuitem"
                      className="chat-composer__menu-item"
                      title={canAttach ? undefined : chat.attachNeedVisionModel}
                      disabled={!canAttach}
                      onClick={() => {
                        setComposerMenuOpen(false)
                        handleAttachClick()
                      }}
                    >
                      <Paperclip size={16} aria-hidden="true" />
                      <span>{chat.attach}</span>
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      className="chat-composer__menu-item"
                      onClick={() => {
                        setComposerMenuOpen(false)
                        setDocumentsPanelOpen((prev) => !prev)
                      }}
                    >
                      <FileText size={16} aria-hidden="true" />
                      <span>{chat.documents}</span>
                      {conversationRagDocuments.length > 0 && (
                        <span className="chat-composer__menu-badge">{conversationRagDocuments.length}</span>
                      )}
                    </button>
                    {templates.length > 0 && (
                      <button
                        type="button"
                        role="menuitem"
                        className="chat-composer__menu-item"
                        onClick={() => {
                          setComposerMenuOpen(false)
                          setTemplatesPanelOpen((prev) => !prev)
                        }}
                      >
                        <LayoutTemplate size={16} aria-hidden="true" />
                        <span>{chat.template}</span>
                      </button>
                    )}
                  </div>
                )}
              </div>
              <label className="visually-hidden" htmlFor="chat-input">{chat.message}</label>
              <textarea
                className="form-textarea chat-composer__input"
                id="chat-input"
                rows={input.length > 400 ? 6 : 2}
                placeholder={chat.placeholder}
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
                  aria-label={common.stop}
                  onClick={handleStop}
                  disabled={!sending}
                >
                  {sending && <span className="chat-btn__spinner" aria-hidden="true" />}
                  {common.stop}
                </button>
                <button
                  type="submit"
                  className="chat-btn chat-btn--primary"
                  aria-label={common.send}
                  disabled={!canSend}
                  title={
                    !selectedModel && selectableModels.length === 0
                      ? chat.sendNeedModel
                      : selectableModels.length === 0
                        ? chat.sendNeedModel
                      : !selectedAvailable
                        ? chat.sendUnavailable
                        : attachment && !selectedModelVisionCapable
                          ? chat.sendNeedVisionModel
                          : inputOverLimit
                            ? chat.sendTooLong
                            : !input.trim()
                              ? chat.sendNeedInput
                              : chat.sendTitle
                  }
                >
                  {common.send}
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
                ? chat.generating
                : selectableModels.length === 0
                  ? chat.noModels
                  : `${formatTokenCount(input.length, CONTENT_LIMITS.CHAT_MESSAGE_MAX)} · ${chat.hintSend}`}
            </p>
          </footer>
        </div>
      </div>
    </AppLayout>
  )
}
