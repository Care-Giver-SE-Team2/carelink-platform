package sg.nus.carelink.careplan.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.careplan.domain.model.CareActivity;

/** The catalog keeps the two codes family applications already store, with their care plan labels. */
class CareActivityControllerTest {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CareActivityController()).build();

	@Test
	void listsEveryActivityWithItsLabelAndCategory() throws Exception {
		mvc.perform(get("/api/care-activities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(CareActivity.values().length))
				.andExpect(jsonPath("$[0].code").value("BATHING"))
				.andExpect(jsonPath("$[0].label").value("Bathing assistance"))
				.andExpect(jsonPath("$[0].category").value("Personal care"))
				.andExpect(jsonPath("$[?(@.code == 'VITALS')].label").value("Vital-sign check"))
				.andExpect(jsonPath("$[?(@.code == 'VITALS')].category").value("Health monitoring"));
	}
}
