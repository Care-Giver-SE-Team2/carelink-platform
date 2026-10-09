package sg.nus.carelink.shared.security;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.profile.application.FamilyElderProfile;
import sg.nus.carelink.profile.application.FamilyElderProfileService;
import sg.nus.carelink.profile.controller.FamilyElderProfileController;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding.AccessScope;

/** Actual security filters, method authorization, JSON validation and safe response projection. */
@WebMvcTest(FamilyElderProfileController.class)
@Import(SecurityConfig.class)
class FamilyElderProfileHttpTest {
    private static final String PATH = "/api/family/elders";
    @Autowired private MockMvc mvc;
    @MockitoBean private FamilyElderProfileService profiles;
    @MockitoBean private UserDetailsService users;

    private FamilyElderProfile profile() {
        return new FamilyElderProfile(1L, "Tan Mei", Elder.Gender.FEMALE, LocalDate.of(1948, 2, 3),
                "81234567", "Example Road", "123456", "Hokkien", true,
                Elder.MobilityLevel.INDEPENDENT, AccessScope.FULL);
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ELDER", "CAREGIVER"})
    void requiresFamilyRoleForReadsAndWrites(String role) throws Exception {
        mvc.perform(get(PATH).with(user("other").roles(role))).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/1").with(user("other").roles(role))).andExpect(status().isForbidden());
        mvc.perform(put(PATH + "/1").with(user("other").roles(role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Tan Mei\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void requiresCsrfForSaving() throws Exception {
        mvc.perform(put(PATH + "/1").with(user("family").roles("FAMILY"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Tan Mei\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void listsOnlySafeFamilyProjection() throws Exception {
        when(profiles.list("family")).thenReturn(List.of(profile()));
        mvc.perform(get(PATH).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].accessScope").value("FULL"))
                .andExpect(jsonPath("$[0].medicalNotes").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andExpect(jsonPath("$[0].sector").doesNotExist());
    }

    @Test
    void readsUsingSessionIdentity() throws Exception {
        when(profiles.get("family", 1L)).thenReturn(profile());
        mvc.perform(get(PATH + "/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Tan Mei"));
        verify(profiles).get("family", 1L);
    }

    @Test
    void savesNormalizedBasicsUsingSessionIdentity() throws Exception {
        when(profiles.update(eq("family"), eq(1L), any())).thenReturn(profile());
        mvc.perform(put(PATH + "/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":" Tan Mei ","postalCode":" 123456 ","phone":" ","dateOfBirth":"1948-02-03"}
                        """))
                .andExpect(status().isOk());
        verify(profiles).update("family", 1L, new ElderBasicDetails("Tan Mei", null,
                LocalDate.of(1948, 2, 3), null, null, "123456", null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"fullName\":\" \"}",
        "{\"fullName\":\"Tan Mei\",\"dateOfBirth\":\"2999-01-01\"}",
        "{\"fullName\":\"Tan Mei\",\"postalCode\":\"12345\"}",
        "{\"fullName\":\"Tan Mei\",\"postalCode\":\"ABCDEF\"}",
        "{\"fullName\":\"Tan Mei\",\"mobilityLevel\":\"UNKNOWN\"}"
    })
    void rejectsInvalidBasicDetailsBeforeSave(String body) throws Exception {
        mvc.perform(put(PATH + "/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    @Test
    void revokedAccessReturnsForbidden() throws Exception {
        when(profiles.update(anyString(), anyLong(), any())).thenThrow(new AccessDeniedException("Denied"));
        mvc.perform(put(PATH + "/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Tan Mei\"}"))
                .andExpect(status().isForbidden());
    }
}
