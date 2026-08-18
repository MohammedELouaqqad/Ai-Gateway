package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.config.UploadProperties;
import com.backend.Ai_Gateway.entity.Attachment;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.GuardrailsExceededException;
import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.repositories.AttachmentRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import com.backend.Ai_Gateway.services.dto.AttachmentDownload;
import com.backend.Ai_Gateway.services.dto.AttachmentView;
import com.backend.Ai_Gateway.services.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the parts of the task's own test checklist that don't need a running
 * Spring context / Postgres: magic-byte rejection, size limit, ownership (IDOR), upload rate
 * limit. "Unauthenticated upload → 401" and "cross-user fetch → HTTP 404" at the real HTTP
 * layer belong in a {@code @SpringBootTest} integration test mirroring
 * {@code SecurityRegressionTest} / {@code ApiKeyAuthIntegrationTest} — not written here since
 * this environment can't run the Postgres-backed IT suite to verify it.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};
    private static final byte[] WEBP_MAGIC = {
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
    };

    @Mock AttachmentRepository attachmentRepository;
    @Mock UserRepository userRepository;
    @Mock FileStorageService fileStorageService;

    private AttachmentService service;
    private UploadProperties properties;
    private User owner;

    @BeforeEach
    void setUp() {
        properties = new UploadProperties();
        service = new AttachmentService(attachmentRepository, userRepository, fileStorageService, properties);
        owner = new User();
        owner.setId(1L);
        owner.setEnabled(true);
    }

    @Test
    void sniffContentType_detectsPngJpegWebp() {
        assertThat(AttachmentService.sniffContentType(PNG_MAGIC)).isEqualTo("image/png");
        assertThat(AttachmentService.sniffContentType(JPEG_MAGIC)).isEqualTo("image/jpeg");
        assertThat(AttachmentService.sniffContentType(WEBP_MAGIC)).isEqualTo("image/webp");
    }

    @Test
    void sniffContentType_rejectsUnknownOrForgedBytes() {
        // A text file renamed "photo.png" — extension/declared header lie, magic bytes don't.
        byte[] plainText = "not actually a png".getBytes();
        assertThat(AttachmentService.sniffContentType(plainText)).isNull();
    }

    @Test
    void upload_rejectsFileWithWrongMagicBytesEvenIfHeaderClaimsImagePng() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        MockMultipartFile fake = new MockMultipartFile(
                "file", "photo.png", "image/png", "definitely not a png".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upload(1L, fake));
        assertThat(ex.getMessage()).contains("unsupported or unrecognized type");
    }

    @Test
    void upload_rejectsOversizedFile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        properties.setMaxSizeBytes(4); // smaller than the PNG magic bytes below

        MockMultipartFile tooBig = new MockMultipartFile("file", "a.png", "image/png", PNG_MAGIC);

        assertThrows(IllegalArgumentException.class, () -> service.upload(1L, tooBig));
    }

    @Test
    void upload_unknownUserThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_MAGIC);

        assertThrows(RessourceNotFoundException.class, () -> service.upload(99L, file));
    }

    @Test
    void upload_acceptsValidPngAndPersists() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(42L);
            return a;
        });
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_MAGIC);

        AttachmentView view = service.upload(1L, file);

        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.contentType()).isEqualTo("image/png");
        assertThat(view.filename()).isEqualTo("photo.png");
    }

    @Test
    void upload_deletesDbRowIfDiskWriteFails() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(42L);
            return a;
        });
        org.mockito.Mockito.doThrow(new java.io.IOException("disk full"))
                .when(fileStorageService).store(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_MAGIC);

        assertThrows(IllegalStateException.class, () -> service.upload(1L, file));

        org.mockito.Mockito.verify(attachmentRepository).delete(any(Attachment.class));
    }

    @Test
    void upload_enforcesPerUserRateLimit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        properties.getRateLimit().setUploadsPerHour(1);

        MockMultipartFile file1 = new MockMultipartFile("file", "a.png", "image/png", PNG_MAGIC);
        MockMultipartFile file2 = new MockMultipartFile("file", "b.png", "image/png", PNG_MAGIC);

        service.upload(1L, file1);
        GuardrailsExceededException ex = assertThrows(GuardrailsExceededException.class,
                () -> service.upload(1L, file2));
        assertThat(ex.getReason()).isEqualTo(GuardrailsExceededException.Reason.UPLOAD_RATE_LIMIT);
    }

    @Test
    void download_ownerCanReadTheirOwnFile() throws Exception {
        Attachment attachment = attachmentEntity(owner);
        when(attachmentRepository.findById(7L)).thenReturn(Optional.of(attachment));
        when(fileStorageService.read(attachment.getStorageKey()))
                .thenReturn(new ByteArrayInputStream(PNG_MAGIC));

        AttachmentDownload download = service.download(1L, 7L);

        assertThat(download.contentType()).isEqualTo("image/png");
        assertThat(download.filename()).isEqualTo("photo.png");
    }

    @Test
    void download_otherUserGetsNotFoundNotForbidden_avoidsConfirmingOwnership() {
        Attachment attachment = attachmentEntity(owner);
        when(attachmentRepository.findById(7L)).thenReturn(Optional.of(attachment));

        // userId 2 is NOT the owner (owner.id == 1) — must be indistinguishable from
        // "attachment doesn't exist" (404), never a 403 that would confirm the ID is valid.
        assertThrows(RessourceNotFoundException.class, () -> service.download(2L, 7L));
    }

    private Attachment attachmentEntity(User owner) {
        Attachment attachment = new Attachment();
        attachment.setId(7L);
        attachment.setOwner(owner);
        attachment.setStorageKey("11111111-1111-1111-1111-111111111111");
        attachment.setOriginalFilename("photo.png");
        attachment.setContentType("image/png");
        attachment.setSizeBytes(PNG_MAGIC.length);
        return attachment;
    }
}
