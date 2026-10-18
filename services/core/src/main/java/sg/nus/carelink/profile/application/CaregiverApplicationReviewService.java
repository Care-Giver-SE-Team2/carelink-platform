package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.CaregiverApplication;

/**
 * UC-MG02 for the manager: the caregiver applications waiting for an answer, each with the
 * certificates the applicant uploaded, and the approve / reject decision. Admission is decided
 * through {@link CaregiverAdmissionCommands} only; the certificates are shown for context and are
 * published or rejected one by one in the certification register (UC-MG06), never by approval.
 */
@Service
public class CaregiverApplicationReviewService {

	/** Register states that are not something the applicant holds or is waiting on. */
	private static final Set<String> HIDDEN_STATES = Set.of("REJECTED", "REVOKED");

	private final CaregiverApplicationReader reader;
	private final CaregiverAdmissionCommands commands;
	private final CredentialReviewService register;
	private final CaregiverCredentialService documents;

	public CaregiverApplicationReviewService(CaregiverApplicationReader reader, CaregiverAdmissionCommands commands,
			CredentialReviewService register, CaregiverCredentialService documents) {
		this.reader = reader;
		this.commands = commands;
		this.register = register;
		this.documents = documents;
	}

	/** One uploaded certificate, with its first scan; the scan fields are null when no file is readable. */
	public record Certificate(Long id, String credentialTypeName, String state, String certificateNo,
			String issuingBody, LocalDate validFrom, LocalDate expiryDate, String fileName, String scanUrl,
			String scanContentType) {
	}

	/**
	 * A pending application as the manager reviews it. {@code version} is the admission version the
	 * decision must quote, so a decision made on a stale screen is refused.
	 */
	public record Item(Long id, Long caregiverId, String username, String fullName, String phone, String dialects,
			String requestedSector, LocalDateTime submittedAt, int roundNumber, long version,
			List<Certificate> certificates) {
	}

	public record Page(List<Item> items, int page, int size, long total) {
	}

	/** What a decision did: the application's new status and the caregiver it admitted or turned down. */
	public record Decision(Long applicationId, Long caregiverId, String status) {
	}

	/** Pending applications, newest first. */
	@Transactional(readOnly = true)
	public Page pending(String managerUsername, int page, int size) {
		var applications = reader.pendingApplications(managerUsername, page, size);
		var certificates = certificatesByCaregiver();
		var items = applications.items().stream()
				.map(a -> item(managerUsername, reader.applicationForManager(managerUsername, a.id()), certificates))
				.toList();
		return new Page(items, applications.page(), applications.size(), applications.total());
	}

	@Transactional(readOnly = true)
	public Item one(String managerUsername, Long applicationId) {
		return item(managerUsername, reader.applicationForManager(managerUsername, applicationId), certificatesByCaregiver());
	}

	/** Admits the caregiver; {@code message}, if any, is kept as the review note the applicant sees. */
	public Decision approve(String managerUsername, Long applicationId, String message, long expectedVersion,
			String clientRequestId) {
		return decision(commands.decide(managerUsername, applicationId, true, message, expectedVersion, clientRequestId));
	}

	/** Turns the application down; the applicant sees {@code reason} and may edit and resubmit. */
	public Decision reject(String managerUsername, Long applicationId, String reason, long expectedVersion,
			String clientRequestId) {
		return decision(commands.decide(managerUsername, applicationId, false, reason, expectedVersion, clientRequestId));
	}

	private Map<Long, List<CredentialRegisterRow>> certificatesByCaregiver() {
		return register.register().stream()
				.filter(row -> !HIDDEN_STATES.contains(row.state()))
				.collect(Collectors.groupingBy(CredentialRegisterRow::caregiverId));
	}

	private Item item(String managerUsername, CaregiverApplication.View view,
			Map<Long, List<CredentialRegisterRow>> certificates) {
		var application = view.application();
		var details = application.details();
		var uploaded = certificates.getOrDefault(view.caregiverId(), List.of()).stream()
				.map(row -> certificate(managerUsername, row))
				.toList();
		return new Item(application.id(), view.caregiverId(), view.username(), details.fullName(), details.phone(),
				details.declaredDialects(), details.requestedSector(), application.submittedAt(),
				application.roundNumber(), view.version(), uploaded);
	}

	private Certificate certificate(String managerUsername, CredentialRegisterRow row) {
		var scan = documents.documents(managerUsername, row.id()).stream().filter(d -> d.contentAvailable()).findFirst().orElse(null);
		return new Certificate(row.id(), row.credentialTypeName(), row.state(), row.certificateNo(), row.issuingBody(),
				row.validFrom(), row.expiryDate(), scan == null ? null : scan.fileName(), scan == null ? null : scan.contentUrl(), scan == null ? null : scan.contentType());
	}

	private static Decision decision(CaregiverApplication.View view) {
		var application = view.application();
		return new Decision(application.id(), view.caregiverId(), application.status().name());
	}
}
