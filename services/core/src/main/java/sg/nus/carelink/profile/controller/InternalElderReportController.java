package sg.nus.carelink.profile.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.profile.application.ElderReportProfiles;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** profile's part of core's internal API ({@link CoreApi}) for reports: an elder as a report describes them. */
@RestController
@RequestMapping("/internal/v1")
public class InternalElderReportController {

	private final ElderReportProfiles profiles;

	InternalElderReportController(ElderReportProfiles profiles) {
		this.profiles = profiles;
	}

	@GetMapping("/elders/{elderId}/report-profile")
	public CoreApi.ElderReportProfile elderReportProfile(@PathVariable Long elderId) {
		return profiles.find(elderId)
				.map(profile -> new CoreApi.ElderReportProfile(profile.elderId(), profile.fullName(), profile.gender(),
						profile.dateOfBirth(), profile.mobilityLevel(), profile.livesAlone(), profile.medicalNotes(),
						profile.primaryCaregiverId(), profile.primaryCaregiverName(), profile.planVersion(),
						profile.planWeeklyHours()))
				.orElseThrow(() -> new ResourceNotFound("Elder", elderId));
	}

}
