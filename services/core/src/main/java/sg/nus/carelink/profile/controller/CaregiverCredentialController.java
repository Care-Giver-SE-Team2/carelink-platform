package sg.nus.carelink.profile.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import sg.nus.carelink.profile.application.CaregiverCredentialService;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.Upload;
import sg.nus.carelink.profile.domain.repository.CredentialSubmissionStore.Result;

/** Multipart creation only: review remains in the existing manager controller. */
@RestController
@RequestMapping("/api/caregivers/me")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverCredentialController {
    private final CaregiverCredentialService service;
    public CaregiverCredentialController(CaregiverCredentialService service) { this.service = service; }

    @GetMapping("/credential-types")
    public List<CredentialType> types(Authentication auth) { return service.types(auth.getName()); }
    @GetMapping("/credentials")
    public CaregiverCredentialService.Page list(Authentication auth, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.list(auth.getName(), page, size); }
    @GetMapping("/credentials/{id}")
    public CaregiverCredentialService.View detail(Authentication auth, @PathVariable Long id) {
        return service.detail(auth.getName(), id);
    }

    @PostMapping(value={"/credentials", "/credentials/{id}/renewals", "/credentials/{id}/resubmissions"}, consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result submit(Authentication auth, jakarta.servlet.http.HttpServletRequest http,
            @PathVariable(required=false) Long id,
            @RequestParam(required=false) Long credentialTypeId,
            @RequestParam(required=false) String certificateNo, @RequestParam String issuingBody,
            @RequestParam LocalDate validFrom, @RequestParam(required=false) LocalDate expiryDate,
            @RequestParam(defaultValue="false") boolean noExpiry, @RequestParam String clientRequestId,
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files.isEmpty() || files.size() > 3 || files.stream().anyMatch(f -> f.isEmpty() || f.getSize() > 5L * 1024 * 1024))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select 1 to 3 non-empty files, each at most 5 MiB.");
        String action = "NEW";
        if (id != null) action = http.getRequestURI().endsWith("/resubmissions") ? "RESUBMIT" : "RENEWAL";
        List<Upload> uploads = new ArrayList<>();
        for (MultipartFile file : files) uploads.add(new Upload(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
        return service.submit(auth.getName(), action, id, new CaregiverCredentialService.Request(credentialTypeId,
                certificateNo, issuingBody, validFrom, expiryDate, noExpiry, clientRequestId), uploads);
    }
}
