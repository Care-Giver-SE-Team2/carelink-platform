package sg.nus.carelink.profile.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.profile.application.FamilyRegistrationService;
import sg.nus.carelink.profile.application.InMemoryFamilyMemberRepository;
import sg.nus.carelink.shared.security.Role;

/** HTTP mapping and format validation of family sign-up; FamilyRegistrationApiIT covers security and storage. */
class FamilyRegistrationControllerTest {

	private MockMvc mvc;

	@BeforeEach
	void prepareController() {
		AccountIssuer accounts = new AccountIssuer() {
			@Override
			public IssuedAccount issue(String displayName, Role role) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Long register(String username, String displayName, String rawPassword, Role role) {
				return 700L;
			}
		};
		var service = new FamilyRegistrationService(accounts, new InMemoryFamilyMemberRepository());
		mvc = MockMvcBuilders.standaloneSetup(new FamilyRegistrationController(service)).build();
	}

	@Test
	void createsTheAccountAndReturnsTheStrippedDetails() throws Exception {
		mvc.perform(post("/api/family-registrations").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":" lim.family ","password":"chosen-password","fullName":"  Lim Wei Ling ","phone":" 9123 4567 "}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("lim.family"))
				.andExpect(jsonPath("$.fullName").value("Lim Wei Ling"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Lim.Family | chosen-password | Lim Wei Ling | 91234567",
			"li         | chosen-password | Lim Wei Ling | 91234567",
			"lim family | chosen-password | Lim Wei Ling | 91234567",
			"lim.family | short           | Lim Wei Ling | 91234567",
			"lim.family | chosen-password | ' '          | 91234567",
			"lim.family | chosen-password | Lim Wei Ling | call me",
			"lim.family | chosen-password | Lim Wei Ling | 61234567",
			"lim.family | chosen-password | Lim Wei Ling | 1234 5678",
			"lim.family | chosen-password | Lim Wei 2    | 91234567"})
	void rejectsMalformedDetails(String username, String password, String fullName, String phone) throws Exception {
		mvc.perform(post("/api/family-registrations").contentType(MediaType.APPLICATION_JSON).content("""
				{"username":"%s","password":"%s","fullName":"%s","phone":"%s"}
				""".formatted(username, password, fullName, phone)))
				.andExpect(status().isBadRequest());
	}
}
