package sg.nus.carelink.profile.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.profile.application.ElderSummary;
import sg.nus.carelink.profile.application.FamilyElderQueryService;
import sg.nus.carelink.profile.application.ProfileService;
import sg.nus.carelink.profile.domain.model.Elder;

/** HTTP surface only: status codes for found and not found. Security is tested at the filter-chain level. */
class ProfileControllerTest {

	private final ProfileService service = mock(ProfileService.class);
	private final FamilyElderQueryService familyElders = mock(FamilyElderQueryService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProfileController(service, familyElders)).build();

	@Test
	void returns200WithTheRecord() throws Exception {
		when(service.findElder(1L)).thenReturn(Optional.of(new Elder(
				1L,
				2L,
				"v3",
				Elder.Gender.MALE,
				LocalDate.of(2026, 9, 6),
				"v6",
				"v7",
				"v8",
				"v9",
				"v10",
				Boolean.TRUE,
				Elder.MobilityLevel.INDEPENDENT,
				Elder.ContinuityPreference.PREFERRED,
				"v14",
				LocalDateTime.of(2026, 9, 6, 10, 15),
				LocalDateTime.of(2026, 9, 6, 10, 16))));

		mvc.perform(get("/api/elders/1")).andExpect(status().isOk());
	}

	@Test
	void returns404WhenMissing() throws Exception {
		when(service.findElder(2L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/elders/2")).andExpect(status().isNotFound());
	}

	@Test
	void returns200WithTheList() throws Exception {
		Elder elder = new Elder(
				1L, 2L, "v3", Elder.Gender.MALE, LocalDate.of(2026, 9, 6), "v6", "v7", "v8", "v9",
				"v10", Boolean.TRUE, Elder.MobilityLevel.INDEPENDENT, Elder.ContinuityPreference.PREFERRED,
				"v14", LocalDateTime.of(2026, 9, 6, 10, 15), LocalDateTime.of(2026, 9, 6, 10, 16));
		when(service.listElders()).thenReturn(List.of(new ElderSummary(elder, "draft", 2, null)));

		mvc.perform(get("/api/elders")
				.principal(new TestingAuthenticationToken("manager", null, "ROLE_MANAGER")))
				.andExpect(status().isOk());
		verifyNoInteractions(familyElders);
	}

	@Test
	void familyUsesTheScopedQueryAndReceivesAnEmptyArrayWithoutBindings() throws Exception {
		when(familyElders.listForFamily("family-a")).thenReturn(List.of());

		mvc.perform(get("/api/elders")
				.principal(new TestingAuthenticationToken("family-a", null, "ROLE_FAMILY")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
		verifyNoInteractions(service);
	}
}
