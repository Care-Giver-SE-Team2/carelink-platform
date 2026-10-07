package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.service.IntakeScreening.CheckKey;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/** Orchestration of the manager's intake review: who applied, the screening, and what an answer saves. */
class IntakeReviewServiceTest {

	/** 2026-10-05 10:00 in Singapore. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T02:00:00Z"), ZoneId.of("UTC"));

	private final InMemoryIntakeApplicationRepository applications = new InMemoryIntakeApplicationRepository();
	private final InMemoryFamilyMemberRepository families = new InMemoryFamilyMemberRepository();
	private final InMemoryElderRepository elders = new InMemoryElderRepository();
	private final InMemoryCaregiverRepository caregivers = new InMemoryCaregiverRepository();
	private final UserDirectory users = mock(UserDirectory.class);
	/** Hands out user ids from 500 and records what it was asked for. */
	private final List<String> issuedFor = new ArrayList<>();
	private final AccountIssuer accounts = (displayName, role) -> {
		issuedFor.add(displayName + "/" + role);
		return new AccountIssuer.IssuedAccount(500L + issuedFor.size(), "login" + issuedFor.size(), "Temp" + issuedFor.size());
	};
	private final IntakeReviewService service =
			new IntakeReviewService(applications, families, elders, caregivers, users, accounts, CLOCK);

	@BeforeEach
	void seed() {
		families.save(new FamilyMember(7L, 70L, "Grace Tan Wei Ling", "+65 9123 4488", null, null, null));
		when(users.findById(70L)).thenReturn(Optional.of(new AppUser(70L, "grace.tan", "Grace", Set.of(Role.FAMILY), true)));
		elders.save(new Elder(null, null, "Chan Bee Choo", null, null, null, "Blk 217 Bishan St 23", "570217", "S31",
				null, null, null, Elder.ContinuityPreference.PREFERRED, null, null, null));
		caregivers.save(new Caregiver(null, 101L, "Siti", null, "S31", "Hokkien", Caregiver.Status.AVAILABLE, null, null));
	}

	@Test
	void pendingApplicationsComeWithTheApplicantTheSectorAndTheChecks() {
		IntakeApplication submitted = applications.save(application("Tan Bee Choo", "570230"));
		applications.save(application("Answered", "570230").decline(1L, "No", LocalDateTime.of(2026, 10, 1, 0, 0)));

		List<IntakeReviewRow> rows = service.pending();

		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.application().id()).isEqualTo(submitted.id());
			assertThat(row.applicant()).isEqualTo(new IntakeReviewRow.Applicant("Grace Tan Wei Ling", "grace.tan", "+65 9123 4488"));
			assertThat(row.sector()).isEqualTo("S31");
			assertThat(row.checks()).extracting(c -> c.key()).containsExactly(CheckKey.CONTACT, CheckKey.SECTOR,
					CheckKey.DIALECT);
			assertThat(row.checks()).allMatch(c -> c.pass());
		});
	}

	@Test
	void approvingCreatesTheElderInTheSectorWithALoginAndLinksTheApplication() {
		Long id = applications.save(application("Tan Bee Choo", "570230")).id();

		IntakeApproval approval = service.approve(id, 9L, "Welcome");
		IntakeApplication approved = approval.application();

		Elder created = elders.findById(approved.elderId()).orElseThrow();
		assertThat(created.fullName()).isEqualTo("Tan Bee Choo");
		assertThat(created.sector()).isEqualTo("S31");
		assertThat(issuedFor).containsExactly("Tan Bee Choo/ELDER");
		assertThat(created.userId()).isEqualTo(501L);
		assertThat(approval.username()).isEqualTo("login1");
		assertThat(approval.temporaryPassword()).isEqualTo("Temp1");
		assertThat(applications.findById(id).orElseThrow()).satisfies(stored -> {
			assertThat(stored.status()).isEqualTo(IntakeApplication.Status.APPROVED);
			assertThat(stored.reviewedByUserId()).isEqualTo(9L);
			assertThat(stored.reviewRemarks()).isEqualTo("Welcome");
			assertThat(stored.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 2, 0)); // UTC
			assertThat(stored.elderId()).isEqualTo(created.id());
		});
	}

	@Test
	void anApplicationFromAnUnknownAreaIsApprovedWithoutASector() {
		Long id = applications.save(application("Ng Kim Lan", "760708")).id();

		IntakeApplication approved = service.approve(id, 9L, null).application();

		assertThat(elders.findById(approved.elderId()).orElseThrow().sector()).isNull();
	}

	@Test
	void decliningSavesTheReasonAndCreatesNoElder() {
		Long id = applications.save(application("Tan Bee Choo", "570230")).id();

		service.decline(id, 9L, "We do not cover this area yet");

		assertThat(applications.findById(id).orElseThrow()).satisfies(stored -> {
			assertThat(stored.status()).isEqualTo(IntakeApplication.Status.REJECTED);
			assertThat(stored.reviewRemarks()).isEqualTo("We do not cover this area yet");
			assertThat(stored.elderId()).isNull();
		});
		assertThat(elders.findAll()).hasSize(1);
		assertThat(issuedFor).isEmpty();
	}

	@Test
	void aSecondAnswerIsRefusedWithoutCreatingAnotherElder() {
		Long id = applications.save(application("Tan Bee Choo", "570230")).id();
		service.approve(id, 9L, null);

		assertThatThrownBy(() -> service.approve(id, 9L, null)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> service.decline(id, 9L, "No")).isInstanceOf(BusinessRuleViolation.class);
		assertThat(elders.findAll()).hasSize(2);
		assertThat(issuedFor).hasSize(1);
	}

	@Test
	void approvingSomeoneAddedSinceTheyAppliedIsRefusedAndCreatesNothing() {
		Long id = applications.save(application("Chan Bee Choo", "570217")).id(); // already seeded as an elder

		assertThatThrownBy(() -> service.approve(id, 9L, null)).isInstanceOfSatisfying(BusinessRuleViolation.class,
				e -> assertThat(e.code()).isEqualTo("ELDER_ALREADY_REGISTERED"));
		assertThat(elders.findAll()).hasSize(1);
		assertThat(issuedFor).isEmpty();
		assertThat(applications.findById(id).orElseThrow().isPending()).isTrue();
	}

	@Test
	void anUnknownApplicationIsNotFound() {
		assertThatThrownBy(() -> service.approve(404L, 9L, null)).isInstanceOf(ResourceNotFound.class);
	}

	private static IntakeApplication application(String name, String postcode) {
		return new IntakeApplication(null, 7L, name, 83, "Blk 230 Bishan St 23", postcode,
				IntakeApplication.MobilityLevel.INDEPENDENT, "Hokkien", List.of(), null,
				IntakeApplication.Status.SUBMITTED, null, null, null, null, null);
	}
}
