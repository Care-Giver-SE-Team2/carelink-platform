package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;

/** Private material: storageKey is never part of a public response. */
public record CredentialDocument(Long id, Long credentialId, String storageKey, String fileName,
        String contentType, long byteSize, String sha256, Long uploadedByUserId,
        LocalDateTime uploadedAt, int displayOrder) { }
