import { useEffect, useState } from 'react'
import { fetchAttachmentBlob } from '../api/files'

interface AttachmentThumbnailProps {
  attachmentId: number
  alt: string
  className?: string
}

/**
 * Renders an uploaded image (GET /api/files/{id}) — can't be a plain `<img src>` since the
 * endpoint requires `Authorization: Bearer` (an <img> tag can never send custom headers).
 * Fetches as a Blob, exposes it via an object URL, revokes it on unmount/id change.
 */
export function AttachmentThumbnail({ attachmentId, alt, className }: AttachmentThumbnailProps) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null
    setUrl(null)
    setFailed(false)

    fetchAttachmentBlob(attachmentId)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [attachmentId])

  if (failed) return null
  if (!url) {
    return <div className={`chat-attachment-thumb chat-attachment-thumb--loading ${className ?? ''}`} aria-hidden="true" />
  }
  return <img className={`chat-attachment-thumb ${className ?? ''}`} src={url} alt={alt} />
}
