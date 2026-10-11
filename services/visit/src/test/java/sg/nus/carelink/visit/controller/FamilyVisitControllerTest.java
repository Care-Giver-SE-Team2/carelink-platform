package sg.nus.carelink.visit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.visit.application.FamilyVisitQueryService;
import sg.nus.carelink.visit.application.FamilyVisitSchedule;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;

/**
 * Checks query binding, validation and the family response projection.
 *
 * @author Wang Zhili
 */
class FamilyVisitControllerTest {

	private final FamilyVisitQueryService queries = mock(FamilyVisitQueryService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FamilyVisitController(queries)).build();
	private static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-09-28T00:30:00+08:00");

	@Test
	void defaultQueryReturnsOnlyTheFamilyProjectionWithOffsetsAndNulls() throws Exception {
		var start = LocalDateTime.of(2026, 9, 28, 9, 0);
		var visit = new Visit(301L, 101L, null, 77L, 88L, null, start, null, null, null,
				Visit.Status.SCHEDULED, start.plusMinutes(15), 99L, 3, start.minusDays(1), start.minusDays(1));
		when(queries.listMine(eq("family-a"), any()))
				.thenReturn(new FamilyVisitSchedule(new VisitPage(List.of(visit), 0, 20, 1), AS_OF));

		mvc.perform(get("/api/visits").principal(() -> "family-a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(301))
				.andExpect(jsonPath("$.items[0].scheduledStart").value("2026-09-28T09:00:00+08:00"))
				.andExpect(jsonPath("$.items[0].asOf").value("2026-09-28T00:30:00+08:00"))
				.andExpect(jsonPath("$.items[0].caregiverId").isEmpty())
				.andExpect(jsonPath("$.items[0].serviceType").isEmpty())
				.andExpect(jsonPath("$.items[0].absenceId").doesNotExist())
				.andExpect(jsonPath("$.items[0].stateDeadline").doesNotExist());
		verify(queries).listMine("family-a", new VisitScheduleFilter(null, null, null, null, null, 0, 20));
	}

	@Test
	void passesAllFiltersAndPaginationAlongsideTheSessionIdentity() throws Exception {
		when(queries.listMine(eq("family-a"), any()))
				.thenReturn(new FamilyVisitSchedule(new VisitPage(List.of(), 2, 1, 0), AS_OF));

		mvc.perform(get("/api/visits").principal(() -> "family-a")
				.param("elderId", "101").param("caregiverId", "201")
				.param("dateFrom", "2026-09-28").param("dateTo", "2026-10-04")
				.param("status", "COMPLETED").param("page", "2").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
		verify(queries).listMine("family-a", new VisitScheduleFilter(101L, 201L,
				LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), Visit.Status.COMPLETED, 2, 1));
	}

	@ParameterizedTest
	@CsvSource({ "status,UNKNOWN", "status,''", "page,-1", "size,201", "elderId,0",
			"caregiverId,-1", "dateFrom,2026-09-28", "dateTo,2026-10-04", "dateFrom,not-a-date" })
	void invalidParametersAreRejectedBeforeTheServiceRuns(String name, String value) throws Exception {
		mvc.perform(get("/api/visits").principal(() -> "family-a").param(name, value))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(queries);
	}
}
