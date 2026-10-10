
package sg.nus.carelink.profile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.profile.application.ElderAccountRegistrationService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ElderAccountRegistrationControllerTest {

    private ElderAccountRegistrationService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(ElderAccountRegistrationService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new ElderAccountRegistrationController(service)
        ).build();
    }

    @Test
    void registersWithTrimmedNameAndUsernameAndNeverReturnsPassword()
            throws Exception {

        when(service.register("Tan Ah Mah", "elder.new", "password123"))
                .thenReturn(42L);

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullName": "  Tan Ah Mah  ",
                          "username": "  elder.new  ",
                          "password": "password123"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.username").value("elder.new"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(service).register("Tan Ah Mah", "elder.new", "password123");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ELDER",
            "ab",
            "bad name",
            "-elder",
            "a@b",
            ""
    })
    void rejectsInvalidUsername(String username) throws Exception {

        String json = """
                {
                  "fullName": "Tan Ah Mah",
                  "username": "%s",
                  "password": "password123"
                }
                """.formatted(username);

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "12345",
            "Tan_Ah_Mah"
    })
    void rejectsBlankOrNonNameValues(String fullName) throws Exception {

        String json = """
                {
                  "fullName": "%s",
                  "username": "elder.new",
                  "password": "password123"
                }
                """.formatted(fullName);

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNameLongerThanTheProfileAllows() throws Exception {

        String json = """
                {
                  "fullName": "%s",
                  "username": "elder.new",
                  "password": "password123"
                }
                """.formatted("a".repeat(101));

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "",
            "1234567"
    })
    void rejectsShortPassword(String password) throws Exception {

        String json = """
                {
                  "fullName": "Tan Ah Mah",
                  "username": "elder.new",
                  "password": "%s"
                }
                """.formatted(password);

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit()
            throws Exception {

        String password = "é".repeat(37);

        String json = """
                {
                  "fullName": "Tan Ah Mah",
                  "username": "elder.new",
                  "password": "%s"
                }
                """.formatted(password);

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingFields() throws Exception {

        mvc.perform(post("/api/elder-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
