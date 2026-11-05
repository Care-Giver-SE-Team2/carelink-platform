package sg.nus.carelink.visit.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.visit.application.CaregiverHistoryService;
import sg.nus.carelink.visit.application.CaregiverEvidenceService;

@RestController
@RequestMapping("/api/caregivers/me/history")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverHistoryController {
    private final CaregiverHistoryService history;
    private final CaregiverEvidenceService evidence;
    public CaregiverHistoryController(CaregiverHistoryService history,CaregiverEvidenceService evidence) { this.history=history; this.evidence=evidence; }
    @GetMapping public CaregiverHistoryService.Month own(Principal principal,@RequestParam String month,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return history.own(principal.getName(),month,status,page,size); }
    @GetMapping("/{visitId}") public CaregiverHistoryService.Detail detail(Principal principal,@PathVariable Long visitId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return history.detail(principal.getName(),visitId,page,size); }
    @GetMapping("/{visitId}/evidence/{evidenceId}/content") public ResponseEntity<byte[]> file(Principal principal,@PathVariable Long visitId,@PathVariable Long evidenceId) {
        var result=evidence.historicalContent(principal.getName(),visitId,evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.metadata().contentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.metadata().fileName(),StandardCharsets.UTF_8).build().toString()).body(result.bytes());
    }
}
