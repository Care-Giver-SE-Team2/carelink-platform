package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.profile.domain.repository.IntakeApplicationRepository;
import sg.nus.carelink.profile.domain.service.DuplicateElderRule;
import sg.nus.carelink.profile.domain.service.IntakeScreening;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * The manager's side of family intake: the applications waiting for an answer, screened, and
 * approving or declining one. Approving creates the elder record from the application and a login
 * for the elder (role ELDER), whose temporary password the manager passes on; no family binding is
 * made, since a binding is always the elder's request, which the elder can now send. What is allowed is
 * IntakeApplication; the sector and checks are IntakeScreening. This class loads, screens and saves.
 */
@Service
@Transactional
public class IntakeReviewService {

	private final IntakeApplicationRepository applications;
	private final FamilyMemberRepository families;
	private final ElderRepository elders;
	private final CaregiverRepository caregivers;
	private final UserDirectory users;
	private final AccountIssuer accounts;
	private final Clock clock;
	private final IntakeScreening screening = new IntakeScreening();

	public IntakeReviewService(IntakeApplicationRepository applications, FamilyMemberRepository families,
			ElderRepository elders, CaregiverRepository caregivers, UserDirectory users, AccountIssuer accounts,
			Clock clock) {
		this.applications = applications;
		this.families = families;
		this.elders = elders;
		this.caregivers = caregivers;
		this.users = users;
		this.accounts = accounts;
		this.clock = clock;
	}

	/** Applications waiting for an answer, newest first, each with its applicant, sector and checks. */
	@Transactional(readOnly = true)
	public List<IntakeReviewRow> pending() {
		List<Elder> allElders = elders.findAll();
		List<Caregiver> allCaregivers = caregivers.findAll();
		return applications.findPending().stream().map(application -> {
			FamilyMember applicant = families.findById(application.applicantFamilyMemberId()).orElse(null);
			IntakeScreening.Result result = screening.screen(application, applicant, allElders, allCaregivers);
			return new IntakeReviewRow(application, applicantOf(applicant), result.sector(), result.checks());
		}).toList();
	}

	/**
	 * Approves an application: creates the elder record in the sector its postcode falls in, a
	 * login for the elder, and links the application to the record, all in one transaction.
	 *
	 * @param message Optional message the family sees with the decision
	 * @return The approved application, carrying the new elder's id, and the elder's login
	 * @throws ResourceNotFound If there is no such application
	 * @throws sg.nus.carelink.shared.error.BusinessRuleViolation If it has already been answered, or the
	 *         elder is already on record (added since the family applied)
	 */
	public IntakeApproval approve(Long applicationId, Long reviewerId, String message) {
		IntakeApplication application = lock(applicationId);
		DuplicateElderRule.requireNotOnRecord(application.targetElderName(), application.postalCode(),
				elders.findByPostalCode(application.postalCode()));
		String sector = screening.sectorFor(application.postalCode(), elders.findAll());
		Elder elder = application.toElder(sector);
		AccountIssuer.IssuedAccount login = accounts.issue(elder.fullName(), Role.ELDER);
		Elder created = elders.save(elder.withUserId(login.userId()));
		IntakeApplication approved = applications.save(application.approve(reviewerId, created.id(), message, now()));
		return new IntakeApproval(approved, login.username(), login.temporaryPassword());
	}

	/**
	 * Declines an application; nothing is created, and the reason is kept on the application.
	 *
	 * @throws ResourceNotFound If there is no such application
	 * @throws sg.nus.carelink.shared.error.BusinessRuleViolation If the reason is blank or it has already been answered
	 */
	public IntakeApplication decline(Long applicationId, Long reviewerId, String message) {
		return applications.save(lock(applicationId).decline(reviewerId, message, now()));
	}

	private IntakeApplication lock(Long applicationId) {
		return applications.findByIdForUpdate(applicationId)
				.orElseThrow(() -> new ResourceNotFound("Intake application", applicationId));
	}

	private IntakeReviewRow.Applicant applicantOf(FamilyMember family) {
		if (family == null) {
			return new IntakeReviewRow.Applicant("Family member", null, null);
		}
		String username = family.userId() == null ? null
				: users.findById(family.userId()).map(user -> user.username()).orElse(null);
		return new IntakeReviewRow.Applicant(family.fullName(), username, family.phone());
	}

	/** Stored as UTC, like created_at, which the family app reads back as UTC. */
	private LocalDateTime now() {
		return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
	}
}
