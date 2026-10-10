package sg.nus.carelink.visit.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.visit.application.CaregiverWorkService;

/** HTTP binding tests; real role/session and database isolation are exercised in CaregiverWorkIT. */
class CaregiverWorkControllerTest {
    private final CaregiverWorkService service = mock(CaregiverWorkService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CaregiverWorkController(service)).build();
    private final LocalDate day = LocalDate.of(2026, 9, 25);

    @Test
    void profileUsesTheAuthenticatedPrincipalNotAQueryParameter() throws Exception {
        when(service.profile("demo-a")).thenReturn(new CaregiverWorkDirectory.Profile(
                2L, 20L, "Caregiver A", null, null, null, "AVAILABLE"));
        mvc.perform(get("/api/caregivers/me").principal(() -> "demo-a").param("caregiverId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.fullName").value("Caregiver A"));
        verify(service).profile("demo-a");
    }

    @Test
    void scheduleBindsBothDatesAndSerializesTheExistingShape() throws Exception {
        when(service.schedule("demo-a", day, day.plusDays(6))).thenReturn(new CaregiverWorkService.Schedule(
                day, day.plusDays(6), "Asia/Singapore", List.of(), List.of(),
                new CaregiverWorkDirectory.CredentialAlertContext(day,30,false)));
        mvc.perform(get("/api/caregivers/me/schedule").principal(() -> "demo-a")
                .param("dateFrom", day.toString()).param("dateTo", day.plusDays(6).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.upcomingVisits").isEmpty())
                .andExpect(jsonPath("$.certificationAlerts").isEmpty())
                .andExpect(jsonPath("$.credentialAlertContext.asOfDate").value("2026-09-25"))
                .andExpect(jsonPath("$.credentialAlertContext.warningDays").value(30))
                .andExpect(jsonPath("$.credentialAlertContext.reviewRequired").value(false));
        verify(service).schedule("demo-a", day, day.plusDays(6));
    }

    @Test
    void invalidRangeRetainsBadRequestStatus() throws Exception {
        when(service.schedule("demo-a", day, null)).thenThrow(new CaregiverWorkService.InvalidDateRange());
        mvc.perform(get("/api/caregivers/me/schedule").principal(() -> "demo-a")
                .param("dateFrom", day.toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void workPackBindsVisitIdAndUsesTheAuthenticatedPrincipal() throws Exception {
        var elder = new CaregiverWorkDirectory.ElderView(3L, "Mei", "Address", "North", List.of(), null, null);
        when(service.workPack("demo-a", 7L)).thenReturn(new CaregiverWorkService.WorkPack(
                null, elder, 4L, 1, List.of("Hygiene"), List.of(), List.of("CHECKLIST")));
        mvc.perform(get("/api/visits/7/work-pack").principal(() -> "demo-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.carePlanVersion").value(1))
                .andExpect(jsonPath("$.elder.preferredName").value("Mei"))
                .andExpect(jsonPath("$.elder.medicalNotes").doesNotExist());
        verify(service).workPack("demo-a", 7L);
    }
}
