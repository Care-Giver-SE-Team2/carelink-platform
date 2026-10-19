package sg.nus.carelink.rostering.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.rostering.application.CaregiverWorkSettingsService;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;

@RestController
@RequestMapping("/api/caregivers/me")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverWorkSettingsController {
    private final CaregiverWorkSettingsService service;
    public CaregiverWorkSettingsController(CaregiverWorkSettingsService service) { this.service=service; }
    @GetMapping("/work-settings") public View settings(Principal principal) { return service.own(principal.getName(),null,null); }
    @GetMapping("/availability") public View availability(Principal principal,@RequestParam LocalDate dateFrom,@RequestParam LocalDate dateTo) {
        return service.own(principal.getName(),dateFrom,dateTo);
    }
    @PutMapping("/availability/{date}") public Snapshot replace(Principal principal,@PathVariable LocalDate date,@Valid @RequestBody DayRequest body) {
        return service.replaceDay(principal.getName(),date,body.windows(),body.expectedSettingsVersion(),body.clientRequestId());
    }
    @PutMapping("/work-preferences") public Snapshot preferences(Principal principal,@Valid @RequestBody PreferenceRequest body) {
        return service.preferences(principal.getName(),body.preferences(),body.expectedSettingsVersion(),body.clientRequestId());
    }
    public record DayRequest(@NotNull @Size(max=8) List<Window> windows,@NotNull @PositiveOrZero Long expectedSettingsVersion,@NotBlank String clientRequestId) {
        @JsonAnySetter public void unexpected(String name,Object value) { throw new IllegalArgumentException("Unknown settings field"); }
    }
    public record PreferenceRequest(@NotNull Preferences preferences,@NotNull @PositiveOrZero Long expectedSettingsVersion,@NotBlank String clientRequestId) {
        @JsonAnySetter public void unexpected(String name,Object value) { throw new IllegalArgumentException("Unknown settings field"); }
    }
}
