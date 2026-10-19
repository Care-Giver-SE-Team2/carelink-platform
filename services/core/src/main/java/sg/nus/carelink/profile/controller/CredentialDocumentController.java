package sg.nus.carelink.profile.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.profile.application.CaregiverCredentialService;

/** Narrow read contract for MG06; no changes to manager review or family visibility. */
@RestController
@RequestMapping("/api/credentials/{id}/documents")
@PreAuthorize("hasAnyRole('CAREGIVER','MANAGER')")
public class CredentialDocumentController {
    private final CaregiverCredentialService service;
    public CredentialDocumentController(CaregiverCredentialService service) { this.service = service; }
    @GetMapping
    public List<CaregiverCredentialService.DocumentView> list(Authentication auth, @PathVariable Long id) {
        return service.documents(auth.getName(), id);
    }
    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> content(Authentication auth, @PathVariable Long id, @PathVariable Long documentId) {
        var content = service.content(auth.getName(), id, documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.document().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.document().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }
}
