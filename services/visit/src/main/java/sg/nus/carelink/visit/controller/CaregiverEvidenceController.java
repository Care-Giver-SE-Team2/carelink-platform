package sg.nus.carelink.visit.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.visit.application.CaregiverEvidenceService;
import sg.nus.carelink.visit.domain.model.VisitEvidence;

@RestController
@RequestMapping("/api/visits/{visitId}/evidence")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverEvidenceController {
    private final CaregiverEvidenceService service;
    public CaregiverEvidenceController(CaregiverEvidenceService service) { this.service=service; }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public CaregiverEvidenceService.Saved upload(Principal principal,@PathVariable Long visitId,@RequestParam VisitEvidence.Kind kind,
            @RequestParam(required=false) Long taskId,@RequestParam Integer expectedVersion,@RequestParam UUID clientRequestId,@RequestParam MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>5242880) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a non-empty JPEG/PNG up to 5 MiB.");
        return service.upload(principal.getName(),visitId,kind,taskId,expectedVersion,clientRequestId,file.getOriginalFilename(),file.getContentType(),file.getBytes());
    }
    @GetMapping public CaregiverEvidenceService.Page list(Principal principal,@PathVariable Long visitId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(principal.getName(),visitId,page,size);
    }
    @GetMapping("/{evidenceId}/content") public ResponseEntity<byte[]> content(Principal principal,@PathVariable Long visitId,@PathVariable Long evidenceId) {
        var content=service.content(principal.getName(),visitId,evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.metadata().contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(content.metadata().fileName(),StandardCharsets.UTF_8).build().toString())
                .body(content.bytes());
    }
}
