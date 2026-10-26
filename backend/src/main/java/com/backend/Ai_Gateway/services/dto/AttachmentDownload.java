package com.backend.Ai_Gateway.services.dto;

import java.io.InputStream;

/** Streamed back by {@code FileController#download} — caller owns closing {@code data}. */
public record AttachmentDownload(
        String contentType,
        String filename,
        InputStream data
) {}
