package sg.nus.carelink.visit.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.visit.application.CaregiverVisitExecutionService;
import sg.nus.carelink.visit.controller.dto.CaregiverCheckInRequest;
import sg.nus.carelink.visit.controller.dto.CaregiverTaskResultRequest;

@RestController
@RequestMapping("/api/visits")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverVisitExecutionController {
    private final CaregiverVisitExecutionService service;
    public CaregiverVisitExecutionController(CaregiverVisitExecutionService service) { this.service=service; }
    @PostMapping("/{visitId}/check-in")
    public CaregiverVisitExecutionService.ExecutionResult checkIn(Authentication auth,@PathVariable Long visitId,@Valid @RequestBody CaregiverCheckInRequest input) {
        return service.checkIn(auth.getName(),visitId,input.expectedVersion(),input.clientRequestId(),input.location());
    }
    @PostMapping("/{visitId}/tasks/{taskId}/complete")
    public CaregiverVisitExecutionService.ExecutionResult result(Authentication auth,@PathVariable Long visitId,@PathVariable Long taskId,@Valid @RequestBody CaregiverTaskResultRequest input) {
        return service.taskResult(auth.getName(),visitId,taskId,new CaregiverVisitExecutionService.TaskCommand(input.status(),input.outcome(),input.caregiverNote(),input.expectedVersion(),input.clientRequestId()));
    }
}
