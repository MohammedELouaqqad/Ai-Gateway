package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.services.AttachmentService;
import com.backend.Ai_Gateway.services.UserService;
import com.backend.Ai_Gateway.services.dto.AttachmentDownload;
import com.backend.Ai_Gateway.services.dto.AttachmentView;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Self-service file upload — a user manages only their own files (JWT chain, {@code /api/**}).
 *
 * <p><b>Phase 1</b>: image attachments only (png/jpg/webp), NOT YET referenced by chat messages
 * — see {@code Attachment} / {@code AttachmentRetentionJob} javadoc. Wiring this into
 * {@code /api/chat} (vision-capable models) is a separate, deliberately follow-up change.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final AttachmentService attachmentService;
    private final UserService userService;

    public FileController(AttachmentService attachmentService, UserService userService) {
        this.attachmentService = attachmentService;
        this.userService = userService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView upload(@RequestParam("file") MultipartFile file) {
        Long userId = userService.requireCurrentUserId();
        return attachmentService.upload(userId, file);
    }

    /** Owner-only — see {@code AttachmentService#download} (404, not 403, on mismatch). */
    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id) {
        Long userId = userService.requireCurrentUserId();
        AttachmentDownload download = attachmentService.download(userId, id);
        StreamingResponseBody body = out -> {
            try (var in = download.data()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.filename()).build().toString())
                .body(body);
    }
}
