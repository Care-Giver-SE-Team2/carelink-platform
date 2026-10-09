package sg.nus.carelink.visit.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.visit.application.VisitService;
import sg.nus.carelink.visit.application.FamilyVisitDetailService;
import sg.nus.carelink.visit.application.FamilyVisitTimelineService;
import sg.nus.carelink.visit.application.FamilyVisitTaskService;
import sg.nus.carelink.visit.controller.dto.FamilyVisitResponse;
import sg.nus.carelink.visit.controller.dto.FamilyVisitTimelineEntryResponse;
import sg.nus.carelink.visit.controller.dto.FamilyVisitTaskResponse;
import sg.nus.carelink.visit.domain.model.Visit;

/**
 * Presentation layer of the visit module: HTTP in, HTTP out, status codes. No business
 * rules. Talks to application services, never to a repository (ArchUnit enforces it). Which role
 * may call each endpoint is declared on the method with @PreAuthorize.
 * identity.controller.AuthController is the template; use dto/ for request and response
 * shapes once they differ from the domain model.
 */
@RestController
@RequestMapping("/api/visits")
public class VisitController {

	private final VisitService service;
	private final FamilyVisitDetailService familyVisits;
	private final FamilyVisitTimelineService familyTimeline;
	private final FamilyVisitTaskService familyTasks;
    private final sg.nus.carelink.visit.application.CaregiverWorkService caregiverWork;

	public VisitController(VisitService service, FamilyVisitDetailService familyVisits,
			FamilyVisitTimelineService familyTimeline, FamilyVisitTaskService familyTasks) {
		this(service,familyVisits,familyTimeline,familyTasks,null);
	}
    @org.springframework.beans.factory.annotation.Autowired
    public VisitController(VisitService service, FamilyVisitDetailService familyVisits,
            FamilyVisitTimelineService familyTimeline, FamilyVisitTaskService familyTasks,
            sg.nus.carelink.visit.application.CaregiverWorkService caregiverWork) {
		this.service = service;
		this.familyVisits = familyVisits;
		this.familyTimeline = familyTimeline;
		this.familyTasks = familyTasks;
        this.caregiverWork = caregiverWork;
	}

	/**
	 * The day roster for the manager's Today board; {@code date} defaults to today. Not the
	 * bare collection path, which is the family portal's visit list (FamilyVisitController).
	 */
	@GetMapping("/roster")
	@PreAuthorize("hasRole('MANAGER')")
	public List<Visit> dayRoster(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.findDayRoster(date);
	}

	/** Reads the authenticated role's projection; family access is checked against the visit's elder. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('MANAGER', 'FAMILY')")
	public ResponseEntity<?> get(@PathVariable Long id, Authentication authentication) {
		if (authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_FAMILY"))) {
			var detail = familyVisits.findDetail(authentication.getName(), id);
			return ResponseEntity.ok(FamilyVisitResponse.from(detail.visit(), detail.asOf()));
		}
		return ResponseEntity.of(service.findVisit(id));
	}

	/** Applied history only; internal audit projections for other roles remain unimplemented. */
	@GetMapping("/{visitId}/timeline")
	@PreAuthorize("hasRole('FAMILY')")
	public List<FamilyVisitTimelineEntryResponse> timeline(@PathVariable Long visitId, Authentication authentication) {
		return familyTimeline.findTimeline(authentication.getName(), visitId).stream()
				.map(FamilyVisitTimelineEntryResponse::from).toList();
	}

	/** The role selects a safe projection; a caller cannot request another projection. */
	@GetMapping("/{visitId}/tasks")
	@PreAuthorize("hasAnyRole('FAMILY','CAREGIVER')")
	public List<Object> tasks(@PathVariable Long visitId, Authentication authentication) {
        if (authentication.getAuthorities().stream().noneMatch(role -> role.getAuthority().equals("ROLE_FAMILY"))) {
            return caregiverWork.workPack(authentication.getName(),visitId).tasks().stream().map(Object.class::cast).toList();
        }
		return familyTasks.findTasks(authentication.getName(), visitId).stream()
				.map(FamilyVisitTaskResponse::from).map(Object.class::cast).toList();
	}
}
