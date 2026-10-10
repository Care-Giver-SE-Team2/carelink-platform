package sg.nus.carelink.profile.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyAlertRecipients;
import sg.nus.carelink.profile.application.PrimaryCaregiverLookup;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * profile's part of core's internal API ({@link CoreApi}). Each endpoint hands the call to the
 * application service the split-out code used in-process, so the rules and the errors stay the same.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalProfileController {

	private final FamilyAccessQuery familyAccess;

	private final CaregiverDirectory caregivers;

	private final CaregiverWorkDirectory caregiverWork;

	private final ProfileService profiles;

	private final FamilyMemberRepository familyMembers;

	private final FamilyAlertRecipients alertRecipients;

	private final PrimaryCaregiverLookup primaryCaregivers;

	InternalProfileController(FamilyAccessQuery familyAccess, CaregiverDirectory caregivers,
			CaregiverWorkDirectory caregiverWork, ProfileService profiles, FamilyMemberRepository familyMembers,
			FamilyAlertRecipients alertRecipients, PrimaryCaregiverLookup primaryCaregivers) {
		this.familyAccess = familyAccess;
		this.caregivers = caregivers;
		this.caregiverWork = caregiverWork;
		this.profiles = profiles;
		this.familyMembers = familyMembers;
		this.alertRecipients = alertRecipients;
		this.primaryCaregivers = primaryCaregivers;
	}

	@GetMapping("/family-access/{username}/elders")
	public CoreApi.ReadableElders readableElders(@PathVariable String username) {
		return new CoreApi.ReadableElders(familyAccess.readableElderIds(username));
	}

	@GetMapping("/family-access/{username}/elders/{elderId}")
	public ResponseEntity<Void> checkElderAccess(@PathVariable String username, @PathVariable Long elderId,
			@RequestParam CoreApi.Access access) {
		if (access == CoreApi.Access.WRITE) {
			familyAccess.requireWritableElder(username, elderId);
		}
		else {
			familyAccess.requireReadableElder(username, elderId);
		}
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/caregivers/{caregiverId}/public-profile")
	public CoreApi.CaregiverPublicProfile caregiverPublicProfile(@PathVariable Long caregiverId) {
		return caregivers.findPublicProfile(caregiverId)
				.map(profile -> new CoreApi.CaregiverPublicProfile(profile.id(), profile.fullName(), profile.dialects()))
				.orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));
	}

	@GetMapping("/caregivers/{caregiverId}/public-credentials")
	public List<CoreApi.CaregiverPublicCredential> caregiverPublicCredentials(@PathVariable Long caregiverId) {
		return caregivers.listPublicCredentials(caregiverId).stream()
				.map(credential -> new CoreApi.CaregiverPublicCredential(credential.id(), credential.caregiverId(),
						credential.credentialTypeId(), credential.credentialTypeName(), credential.issuingBody(),
						credential.validFrom(), credential.expiryDate(), credential.status()))
				.toList();
	}

	@GetMapping("/caregivers/by-username/{username}")
	public CoreApi.CaregiverProfile caregiverByUsername(@PathVariable String username) {
		CaregiverWorkDirectory.Profile profile = caregiverWork.require(username);
		return new CoreApi.CaregiverProfile(profile.id(), profile.userId(), profile.fullName(), profile.phone(),
				profile.sector(), profile.dialects(), profile.status());
	}

	@GetMapping("/caregivers/{caregiverId}/credential-alerts")
	public CoreApi.CredentialAlerts credentialAlerts(@PathVariable Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
		CaregiverWorkDirectory.CredentialAlerts alerts = caregiverWork.alerts(caregiverId, today);
		CaregiverWorkDirectory.CredentialAlertContext context = alerts.context();
		return new CoreApi.CredentialAlerts(
				alerts.items().stream()
						.map(alert -> new CoreApi.CredentialAlert(alert.id(), alert.name(), alert.certificateNo(),
								alert.expiryDate(), alert.status(), alert.warning(), alert.daysUntilExpiry(),
								alert.renewalState(), alert.renewalValidFrom()))
						.toList(),
				new CoreApi.CredentialAlertContext(context.asOfDate(), context.warningDays(), context.reviewRequired()));
	}

	@GetMapping("/elders/{elderId}/caregiver-view")
	public CoreApi.ElderView elderCaregiverView(@PathVariable Long elderId) {
		CaregiverWorkDirectory.ElderView elder = caregiverWork.elder(elderId);
		return new CoreApi.ElderView(elder.elderId(), elder.preferredName(), elder.serviceAddress(),
				elder.postalSector(), elder.languageNeeds(), elder.accessNotes(), elder.emergencyNotes());
	}

	@GetMapping("/elders/by-user/{userId}")
	public CoreApi.ElderRef elderByUser(@PathVariable Long userId) {
		return new CoreApi.ElderRef(profiles.requireElderByUserId(userId).id());
	}

	@GetMapping("/family-members/by-user/{userId}")
	public CoreApi.FamilyMemberRef familyMemberByUser(@PathVariable Long userId) {
		return familyMembers.findByUserId(userId)
				.map(member -> new CoreApi.FamilyMemberRef(member.id()))
				.orElseThrow(() -> new ResourceNotFound("Family member for user", userId));
	}

	@GetMapping("/elders/{elderId}/family-members")
	public List<Long> familyMemberIds(@PathVariable Long elderId) {
		return alertRecipients.familyMemberIds(elderId);
	}

	@GetMapping("/elders/{elderId}/family-members/{familyMemberId}/alert-recipient")
	public CoreApi.AlertRecipient alertRecipient(@PathVariable Long elderId, @PathVariable Long familyMemberId) {
		FamilyAlertRecipients.Candidate candidate = alertRecipients.resolve(elderId, familyMemberId);
		return new CoreApi.AlertRecipient(candidate.familyMemberId(), candidate.userId(), candidate.exclusionReason());
	}

	@GetMapping("/elders/{elderId}/primary-caregiver")
	public CoreApi.CaregiverRef primaryCaregiver(@PathVariable Long elderId) {
		return primaryCaregivers.findRosterableCaregiverId(elderId)
				.map(CoreApi.CaregiverRef::new)
				.orElseThrow(() -> new ResourceNotFound("Rosterable primary caregiver for elder", elderId));
	}

}
