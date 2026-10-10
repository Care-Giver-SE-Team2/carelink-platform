package sg.nus.carelink.coreapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * What core offers the other services in place of the in-process calls they made while they were
 * part of core. core serves it under {@code /internal/v1}, which the Ingress does not route, so only
 * the services inside the cluster reach it.
 *
 * <p>Errors come back as they did in-process: 403 as {@link AccessDeniedException}, 404 as
 * {@link CoreNotFound}, and a broken business rule (409) as {@link CoreRuleViolation} with core's
 * rule code. See {@link CoreApiClients}.
 */
@HttpExchange(url = "/internal/v1", accept = "application/json")
public interface CoreApi {

	// ---------- Accounts (identity) ----------

	/** An account as it stands now: whether it may sign in, and its roles. 404 when there is no such account. */
	@GetExchange("/accounts/{username}")
	Account account(@PathVariable String username);

	/** {@link #account}, with an account that does not exist as empty. */
	default Optional<Account> findAccount(String username) {
		try {
			return Optional.of(account(username));
		}
		catch (CoreNotFound notFound) {
			return Optional.empty();
		}
	}

	/** The enabled accounts that hold a role, such as every manager a message goes to. */
	@GetExchange("/accounts/with-role/{role}")
	AccountIds enabledAccountsWithRole(@PathVariable String role);

	// ---------- Family access (profile) ----------

	/** The elders this family account may read. */
	@GetExchange("/family-access/{username}/elders")
	ReadableElders readableElders(@PathVariable String username);

	/** Returns when the account may read (or write) this elder's records; 403 otherwise. */
	@GetExchange("/family-access/{username}/elders/{elderId}")
	void checkElderAccess(@PathVariable String username, @PathVariable Long elderId, @RequestParam Access access);

	// ---------- Caregivers (profile) ----------

	@GetExchange("/caregivers/{caregiverId}/public-profile")
	CaregiverPublicProfile caregiverPublicProfile(@PathVariable Long caregiverId);

	default Optional<CaregiverPublicProfile> findCaregiverPublicProfile(Long caregiverId) {
		try {
			return Optional.of(caregiverPublicProfile(caregiverId));
		}
		catch (CoreNotFound notFound) {
			return Optional.empty();
		}
	}

	@GetExchange("/caregivers/{caregiverId}/public-credentials")
	List<CaregiverPublicCredential> caregiverPublicCredentials(@PathVariable Long caregiverId);

	/** The caregiver behind a signed-in account; 403 when the account is not a caregiver's. */
	@GetExchange("/caregivers/by-username/{username}")
	CaregiverProfile caregiverByUsername(@PathVariable String username);

	@GetExchange("/caregivers/{caregiverId}/credential-alerts")
	CredentialAlerts credentialAlerts(@PathVariable Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today);

	// ---------- Elders and families (profile) ----------

	/** What a caregiver needs to know about an elder before a visit. */
	@GetExchange("/elders/{elderId}/caregiver-view")
	ElderView elderCaregiverView(@PathVariable Long elderId);

	@GetExchange("/elders/by-user/{userId}")
	ElderRef elderByUser(@PathVariable Long userId);

	@GetExchange("/family-members/by-user/{userId}")
	FamilyMemberRef familyMemberByUser(@PathVariable Long userId);

	default Optional<Long> findFamilyMemberIdByUser(Long userId) {
		try {
			return Optional.of(familyMemberByUser(userId).familyMemberId());
		}
		catch (CoreNotFound notFound) {
			return Optional.empty();
		}
	}

	@GetExchange("/elders/{elderId}/family-members")
	List<Long> familyMemberIds(@PathVariable Long elderId);

	/** Whether a family member should be alerted about this elder, and if not, why. */
	@GetExchange("/elders/{elderId}/family-members/{familyMemberId}/alert-recipient")
	AlertRecipient alertRecipient(@PathVariable Long elderId, @PathVariable Long familyMemberId);

	@GetExchange("/elders/{elderId}/primary-caregiver")
	CaregiverRef primaryCaregiver(@PathVariable Long elderId);

	/** The elder's primary caregiver, when one can be rostered. */
	default Optional<Long> findPrimaryCaregiverId(Long elderId) {
		try {
			return Optional.of(primaryCaregiver(elderId).caregiverId());
		}
		catch (CoreNotFound notFound) {
			return Optional.empty();
		}
	}

	/**
	 * What a report says about an elder, in one call: the profile, the primary caregiver and the latest
	 * published plan (the one in force when the report is written). 404 when there is no such elder.
	 */
	@GetExchange("/elders/{elderId}/report-profile")
	ElderReportProfile elderReportProfile(@PathVariable Long elderId);

	// ---------- Care plans (careplan) ----------

	/** The published plan a visit follows, with its tasks. */
	@GetExchange("/care-plans/{planId}/snapshot")
	CarePlanSnapshot carePlanSnapshot(@PathVariable Long planId, @RequestParam Long elderId);

	// ---------- Incidents (incident) ----------

	@PostExchange("/incidents/caregiver-reports")
	IncidentReport reportIncident(@RequestBody CaregiverIncidentRequest request);

	/** An incident as the caregiver who reported it sees it. */
	@GetExchange("/incidents/{incidentId}")
	IncidentReport incident(@PathVariable Long incidentId, @RequestParam Long actor);

	@GetExchange("/incidents")
	IncidentReports incidents(@RequestParam Long actor, @RequestParam Long visitId,
			@RequestParam int page, @RequestParam int size);

	@PostExchange("/incidents/missed-check-ins")
	IncidentRef raiseMissedCheckIn(@RequestBody MissedCheckInRequest request);

	@PostExchange("/incidents/service-disputes")
	IncidentRef raiseServiceDispute(@RequestBody ServiceDisputeRequest request);

	/**
	 * Whether this missed check-in incident is the only incident on the visit, which visit asks before
	 * it lets the caregiver carry on with a visit the missed check-in paused.
	 */
	@GetExchange("/incidents/{incidentId}/sole-missed-check-in")
	SoleIncident soleMissedCheckInIncident(@PathVariable Long incidentId, @RequestParam Long elderId,
			@RequestParam Long visitId);

	// ---------- Visit cover (rostering) ----------

	@GetExchange("/visits/{visitId}/cover-options")
	List<CoverOption> coverOptions(@PathVariable Long visitId);

	@PostExchange("/visits/{visitId}/cover")
	void cover(@PathVariable Long visitId, @RequestBody CoverRequest request);

	/** Whether the caregiver is on approved leave that day. */
	@GetExchange("/caregivers/{caregiverId}/on-leave")
	OnLeave onLeave(@PathVariable Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day);

	// ---------- Records ----------

	enum Access { READ, WRITE }

	/** @param roles the role names, such as {@code FAMILY} or {@code MANAGER} */
	record Account(Long userId, String username, String displayName, boolean enabled, Set<String> roles) {
	}

	record AccountIds(List<Long> userIds) {
	}

	/**
	 * @param planVersion null when the elder has no published plan
	 * @param planWeeklyHours null when the elder has no published plan
	 */
	record ElderReportProfile(Long elderId, String fullName, String gender, LocalDate dateOfBirth, String mobilityLevel,
			Boolean livesAlone, String medicalNotes, Long primaryCaregiverId, String primaryCaregiverName,
			Integer planVersion, BigDecimal planWeeklyHours) {
	}

	record OnLeave(boolean onLeave) {
	}

	record ReadableElders(Set<Long> elderIds) {
	}

	record CaregiverPublicProfile(Long id, String fullName, List<String> dialects) {
	}

	record CaregiverPublicCredential(Long id, Long caregiverId, Long credentialTypeId, String credentialTypeName,
			String issuingBody, LocalDate validFrom, LocalDate expiryDate, String status) {
	}

	record CaregiverProfile(Long id, Long userId, String fullName, String phone, String sector, String dialects,
			String status) {
	}

	record CredentialAlert(Long id, String name, String certificateNo, LocalDate expiryDate, String status,
			String warning, long daysUntilExpiry, String renewalState, LocalDate renewalValidFrom) {
	}

	record CredentialAlertContext(LocalDate asOfDate, int warningDays, boolean reviewRequired) {
	}

	record CredentialAlerts(List<CredentialAlert> items, CredentialAlertContext context) {
	}

	record ElderView(Long elderId, String preferredName, String serviceAddress, String postalSector,
			List<String> languageNeeds, String accessNotes, String emergencyNotes) {
	}

	record ElderRef(Long elderId) {
	}

	record FamilyMemberRef(Long familyMemberId) {
	}

	record CaregiverRef(Long caregiverId) {
	}

	/** {@code exclusionReason} is null when the family member is to be alerted. */
	record AlertRecipient(Long familyMemberId, Long userId, String exclusionReason) {

		public boolean eligible() {
			return exclusionReason == null;
		}

	}

	record CarePlanTask(Long id, String name, String evidenceType) {
	}

	record CarePlanSnapshot(Long id, Integer version, List<CarePlanTask> tasks) {
	}

	record CaregiverIncidentRequest(Long elderId, Long visitId, Long actor, String category, String severity,
			String description) {
	}

	record IncidentReport(Long id, Long visitId, String category, String severity, String description, String status,
			LocalDateTime reportedAt, LocalDateTime respondBy, LocalDateTime resolvedAt) {
	}

	record IncidentReports(List<IncidentReport> items, int page, int size, long totalElements) {
	}

	record MissedCheckInRequest(Long elderId, Long visitId, LocalDateTime dueAt, LocalDateTime observedAt) {
	}

	record ServiceDisputeRequest(Long elderId, Long visitId, Long reportedByUserId, String description) {
	}

	record IncidentRef(Long incidentId) {
	}

	record SoleIncident(boolean sole) {
	}

	/** {@code rank} is null when the caregiver cannot take the visit; {@code reason} says why. */
	record CoverOption(Long caregiverId, String name, Integer rank, String reason) {

		public boolean eligible() {
			return rank != null;
		}

	}

	record CoverRequest(Long caregiverId, Long byUserId) {
	}

}
