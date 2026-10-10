package sg.nus.carelink.profile.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.application.InMemoryFamilyMemberRepository;
import sg.nus.carelink.profile.application.InMemoryIntakeApplicationRepository;
import sg.nus.carelink.profile.application.IntakeQueryService;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.shared.security.Role;

/**
 * Checks HTTP mapping and validation without a database; IntakeListingApiIT and IntakeDetailApiIT verify real
 * security and storage.
 *
 * @author Wang Zhili
 */
class IntakeApplicationControllerTest {

	private MockMvc mvc;
	private InMemoryIntakeApplicationRepository applications;

	@BeforeEach
	void prepareControllerWithInMemoryStorage() {
		var account = new AppUser(7L, "family-a", "Family A", Set.of(Role.FAMILY), true);
		var users = new UserDirectory() {
			@Override
			public Optional<AppUser> findByUsername(String username) {
				return account.username().equals(username) ? Optional.of(account) : Optional.empty();
			}

			@Override
			public Optional<AppUser> findById(Long id) {
				return account.id().equals(id) ? Optional.of(account) : Optional.empty();
			}
		};
		var families = new InMemoryFamilyMemberRepository();
		families.save(new FamilyMember(42L, 7L, "Family A", null, null, null, null));
		applications = new InMemoryIntakeApplicationRepository();
		var queries = new IntakeQueryService(users, families, applications);
		mvc = MockMvcBuilders.standaloneSetup(new IntakeApplicationController(queries)).build();
	}

	@Test
	void readsTheDetailsOfAnApplicationSubmittedByTheCurrentFamily() throws Exception {
		applications.save(submitted(42L));

		mvc.perform(get("/api/intake-applications/1").principal(() -> "family-a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(42))
				.andExpect(jsonPath("$.targetElderName").value("Tan Mei"))
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.reviewedByUserId").doesNotExist());
	}

	@Test
	void rejectsAnExplicitlyEmptyStatusInsteadOfRemovingTheFilter() throws Exception {
		mvc.perform(get("/api/intake-applications").principal(() -> "family-a").param("status", ""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsAnEmptyFirstPageForAFamilyWithoutApplications() throws Exception {
		mvc.perform(get("/api/intake-applications").principal(() -> "family-a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void listsTheFamilyProjectionOfTheCurrentFamilysApplications() throws Exception {
		applications.save(submitted(42L));

		mvc.perform(get("/api/intake-applications").principal(() -> "family-a").param("status", "SUBMITTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].targetElderName").value("Tan Mei"))
				.andExpect(jsonPath("$.items[0].applicantFamilyMemberId").value(42))
				.andExpect(jsonPath("$.items[0].careNeeds").isEmpty())
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-09-15T10:00:00Z"))
				.andExpect(jsonPath("$.items[0].reviewedByUserId").doesNotExist())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	private static IntakeApplication submitted(Long familyMemberId) {
		return new IntakeApplication(null, familyMemberId, "Tan Mei", null, "12 Example Road", "123456",
				IntakeApplication.MobilityLevel.INDEPENDENT, null, null, null, IntakeApplication.Status.SUBMITTED,
				null, null, null, null, null);
	}
}
