package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.testsupport.SharedMySql;

/** Security, real storage, snapshot stability and legacy-queue separation through the HTTP boundary. */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyServiceApplicationApiIT {
    private static final String PATH = "/api/family/service-applications";
    private static final String REQUEST = "{\"elderId\":1,\"careNeeds\":[\" BATHING \",\"VITALS\"],\"notes\":\" Help in the morning \"}";
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyServiceApplicationApiIT.class, "+05:00");
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM care_service_application");
        jdbc.update("DELETE FROM elder_family_binding");
        jdbc.update("DELETE FROM elder");
        jdbc.update("DELETE FROM family_member");
        jdbc.update("DELETE FROM user_role");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("INSERT INTO app_user(id,username,password_hash,display_name) VALUES "
                + "(7,'family','{noop}test','Family'),(8,'other','{noop}test','Other')");
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (7,'FAMILY'),(8,'FAMILY')");
        jdbc.update("INSERT INTO family_member(id,user_id,full_name) VALUES (42,7,'Family'),(43,8,'Other')");
        jdbc.update("INSERT INTO elder(id,user_id,full_name,date_of_birth,phone,address,postal_code,sector,medical_notes) VALUES "
                + "(1,71,'Tan Mei','1948-02-03','81234567','12 Example Road','123456','AMK','Private clinical record'),"
                + "(2,72,'Read only elder',NULL,NULL,'22 Example Road','123456',NULL,NULL),"
                + "(3,73,'Another elder',NULL,NULL,'32 Example Road','654321',NULL,NULL)");
        jdbc.update("INSERT INTO elder_family_binding(elder_id,family_member_id,status,access_scope) VALUES "
                + "(1,42,'ACTIVE','FULL'),(2,42,'ACTIVE','READ_ONLY'),(3,42,'ACTIVE','FULL'),(1,43,'ACTIVE','FULL')");
    }

    /** Exercise EL04, My elders and FM01 together; no fixture grants access after invitation. */
    @Test
    void confirmedInvitationEnablesProfileMaintenanceAndSubmissionUntilElderRevokes() throws Exception {
        long bindingId = invite("FULL");
        jdbc.update("UPDATE elder SET address=NULL,postal_code=NULL WHERE id=1");
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isForbidden());
        deniedSubmission();
        decide(bindingId, true);
        mvc.perform(get("/api/family/elders").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == 1)].accessScope").value("FULL"));
        mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ELDER_PROFILE_INCOMPLETE"));
        updateProfile("12 Saved Road");
        long id = submit(REQUEST);
        updateProfile("34 Changed Road");
        mvc.perform(get(PATH + "/" + id).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elderSnapshot.address").value("12 Saved Road"));
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("34 Changed Road"));
        mvc.perform(delete("/api/elders/me/family-bindings/" + bindingId)
                .with(user("elder").roles("ELDER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        mvc.perform(get(PATH + "/" + id).with(user("family").roles("FAMILY")))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/family/elders/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(profileBody("Not allowed")))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_service_application", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM intake_application", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_plan", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT medical_notes FROM elder WHERE id=1", String.class))
                .isEqualTo("Private clinical record");
    }

    @Test
    void confirmingReadOnlyInvitationNeverGrantsProfileOrServiceWriteAccess() throws Exception {
        long bindingId = invite("READ_ONLY");
        decide(bindingId, true);
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessScope").value("READ_ONLY"));
        mvc.perform(put("/api/family/elders/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(profileBody("Not allowed")))
                .andExpect(status().isForbidden());
        deniedSubmission();
    }

    @Test
    void rejectedInvitationDoesNotEnableProfileReadOrServiceSubmission() throws Exception {
        long bindingId = invite("FULL");
        decide(bindingId, false);
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isForbidden());
        deniedSubmission();
    }

    private long invite(String scope) throws Exception {
        jdbc.update("DELETE FROM elder_family_binding WHERE elder_id=1 AND family_member_id=42");
        jdbc.update("INSERT INTO app_user(id,username,password_hash,display_name) VALUES (71,'elder','{noop}test','Elder')");
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (71,'ELDER')");
        String response = mvc.perform(post("/api/elders/me/family-bindings")
                .with(user("elder").roles("ELDER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"familyUsername\":\"family\",\"relationship\":\"DAUGHTER\",\"primaryContact\":false,\"accessScope\":\"" + scope + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").longValue();
    }

    private void decide(long id, boolean approve) throws Exception {
        mvc.perform(post("/api/family/family-bindings/" + id + "/decision")
                .with(user("family").roles("FAMILY")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":" + approve + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(approve ? "ACTIVE" : "REJECTED"));
    }

    private void updateProfile(String address) throws Exception {
        mvc.perform(put("/api/family/elders/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(profileBody(address)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value(address));
    }

    private String profileBody(String address) {
        return "{\"fullName\":\"Tan Mei\",\"address\":\"" + address + "\",\"postalCode\":\"012345\"}";
    }

    private long submit(String body) throws Exception {
        String response = mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").longValue();
    }

    @Test
    void persistsExistingElderWithServerSnapshotAndUtcTimeWithoutUsingLegacyIntake() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        long id = submit(REQUEST);
        String response = mvc.perform(get(PATH + "/" + id).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elderId").value(1))
                .andExpect(jsonPath("$.elderSnapshot.fullName").value("Tan Mei"))
                .andExpect(jsonPath("$.elderSnapshot.dateOfBirth").value("1948-02-03"))
                .andExpect(jsonPath("$.elderSnapshot.phone").value("81234567"))
                .andExpect(jsonPath("$.elderSnapshot.address").value("12 Example Road"))
                .andExpect(jsonPath("$.elderSnapshot.userId").doesNotExist())
                .andExpect(jsonPath("$.elderSnapshot.medicalNotes").doesNotExist())
                .andExpect(jsonPath("$.elderSnapshot.sector").doesNotExist())
                .andExpect(jsonPath("$.careNeeds[0]").value("BATHING"))
                .andExpect(jsonPath("$.notes").value("Help in the morning"))
                .andReturn().getResponse().getContentAsString();
        Instant created = Instant.parse(json.readTree(response).get("createdAt").stringValue());
        assertThat(created).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT applicant_family_member_id FROM care_service_application WHERE id=?", Long.class, id)).isEqualTo(42L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM intake_application", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_plan", Integer.class)).isZero();
    }

    @Test
    void laterProfileChangesDoNotRewriteAnEarlierApplication() throws Exception {
        long first = submit(REQUEST);
        jdbc.update("UPDATE elder SET full_name='Updated name',address='New address' WHERE id=1");
        long second = submit("{\"elderId\":1,\"careNeeds\":[\"Help with meals\"],\"notes\":null}");
        mvc.perform(get(PATH + "/" + first).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elderSnapshot.fullName").value("Tan Mei"))
                .andExpect(jsonPath("$.elderSnapshot.address").value("12 Example Road"));
        mvc.perform(get(PATH + "/" + second).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elderSnapshot.fullName").value("Updated name"))
                .andExpect(jsonPath("$.careNeeds[0]").value("Help with meals"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_CONFIRMATION", "REJECTED", "REVOKED"})
    void inactiveBindingsCannotSubmit(String state) throws Exception {
        jdbc.update("UPDATE elder_family_binding SET status=? WHERE elder_id=1 AND family_member_id=42", state);
        deniedSubmission();
    }

    @Test
    void unboundReadOnlyAndExpiredBindingsCannotSubmit() throws Exception {
        jdbc.update("UPDATE elder_family_binding SET access_scope='READ_ONLY' WHERE elder_id=1 AND family_member_id=42");
        deniedSubmission();
        jdbc.update("UPDATE elder_family_binding SET access_scope='FULL',expires_at='2000-01-01' WHERE elder_id=1 AND family_member_id=42");
        deniedSubmission();
        jdbc.update("DELETE FROM elder_family_binding WHERE elder_id=1 AND family_member_id=42");
        deniedSubmission();
    }

    private void deniedSubmission() throws Exception {
        mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_service_application", Integer.class)).isZero();
    }

    @Test
    void disabledFamilyCannotSubmitOrRead() throws Exception {
        jdbc.update("UPDATE app_user SET enabled=false WHERE id=7");
        deniedSubmission();
        mvc.perform(get(PATH).with(user("family").roles("FAMILY"))).andExpect(status().isForbidden());
    }

    @Test
    void requiresSessionFamilyRoleAndCsrf() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(user("family").roles("FAMILY"))
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
        for (String role : new String[]{"MANAGER", "CAREGIVER", "ELDER"}) {
            mvc.perform(post(PATH).with(user("family").roles(role)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
            mvc.perform(get(PATH).with(user("family").roles(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void anotherFamilyCannotReadAnApplicationEvenForTheSameBoundElder() throws Exception {
        long id = submit(REQUEST);
        mvc.perform(get(PATH + "/" + id).with(user("other").roles("FAMILY"))).andExpect(status().isNotFound());
        mvc.perform(get(PATH).with(user("other").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PATH + "/999999").with(user("family").roles("FAMILY"))).andExpect(status().isNotFound());
    }

    @Test
    void filtersRevokedSnapshotsBeforeCountingAndPaging() throws Exception {
        long hidden = submit(REQUEST);
        long visible = submit("{\"elderId\":3,\"careNeeds\":[\"VITALS\"]}");
        jdbc.update("UPDATE elder_family_binding SET status='REVOKED' WHERE elder_id=1 AND family_member_id=42");
        mvc.perform(get(PATH).param("size", "1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(visible));
        mvc.perform(get(PATH + "/" + hidden).with(user("family").roles("FAMILY"))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).param("page", "1").param("size", "1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void readOnlyDowngradeStillAllowsOwnedHistoryAndNoBindingReturnsEmptyPage() throws Exception {
        long id = submit(REQUEST);
        jdbc.update("UPDATE elder_family_binding SET access_scope='READ_ONLY' WHERE elder_id=1 AND family_member_id=42");
        mvc.perform(get(PATH + "/" + id).with(user("family").roles("FAMILY"))).andExpect(status().isOk());
        jdbc.update("DELETE FROM elder_family_binding WHERE family_member_id=42");
        mvc.perform(get(PATH).with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void stableNewestFirstPagingSupportsVeryLargeOutOfRangePage() throws Exception {
        long first = submit(REQUEST);
        long second = submit(REQUEST);
        jdbc.update("UPDATE care_service_application SET created_at='2026-10-09 00:00:00'");
        mvc.perform(get(PATH).param("size", "1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).param("page", "1").param("size", "1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first));
        mvc.perform(get(PATH).param("page", "2147483647").param("size", "100").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void incompleteSavedProfileReturnsActionableConflictWithoutSaving() throws Exception {
        jdbc.update("UPDATE elder SET address=NULL WHERE id=1");
        mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ELDER_PROFILE_INCOMPLETE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_service_application", Integer.class)).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidOrForgedInputNeverCreatesAnApplication(String body) throws Exception {
        mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_service_application", Integer.class)).isZero();
    }

    static Stream<String> invalidRequests() {
        return Stream.of("[]", "{}", "{\"elderId\":1}", "{\"elderId\":null,\"careNeeds\":[\"VITALS\"]}",
                "{\"elderId\":0,\"careNeeds\":[\"VITALS\"]}", "{\"elderId\":1.5,\"careNeeds\":[\"VITALS\"]}",
                "{\"elderId\":\"1\",\"careNeeds\":[\"VITALS\"]}", "{\"elderId\":9223372036854775808,\"careNeeds\":[\"VITALS\"]}",
                "{\"elderId\":1,\"careNeeds\":[]}", "{\"elderId\":1,\"careNeeds\":null}",
                "{\"elderId\":1,\"careNeeds\":[null]}", "{\"elderId\":1,\"careNeeds\":[3]}",
                "{\"elderId\":1,\"careNeeds\":[\" \" ]}", "{\"elderId\":1,\"careNeeds\":[\"VITALS\"],\"notes\":3}",
                "{\"elderId\":1,\"careNeeds\":[\"VITALS\"],\"applicantFamilyMemberId\":43}",
                "{\"elderId\":1,\"careNeeds\":[\"VITALS\"],\"elderSnapshot\":{\"fullName\":\"Forged\"}}",
                "{\"elderId\":1,\"careNeeds\":[\"VITALS\"],\"status\":\"APPROVED\"}",
                "{\"elderId\":1,\"careNeeds\":[\"" + "A".repeat(101) + "\"]}",
                "{\"elderId\":1,\"careNeeds\":[\"VITALS\"],\"notes\":\"" + "A".repeat(2001) + "\"}",
                "{\"elderId\":1,\"careNeeds\":[" + String.join(",", java.util.Collections.nCopies(21, "\"VITALS\"")) + "]}");
    }

    @ParameterizedTest
    @MethodSource("invalidPages")
    void rejectsInvalidPagination(String field, String value) throws Exception {
        mvc.perform(get(PATH).param(field, value).with(user("family").roles("FAMILY"))).andExpect(status().isBadRequest());
    }

    static Stream<Arguments> invalidPages() {
        return Stream.of(Arguments.of("page", "-1"), Arguments.of("page", "bad"),
                Arguments.of("size", "0"), Arguments.of("size", "101"));
    }

    @Test
    void databaseWriteFailureReturnsFailureAndLeavesNoApplication() throws Exception {
        jdbc.execute("CREATE TRIGGER reject_service_application BEFORE INSERT ON care_service_application "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Simulated storage failure'");
        try {
            mvc.perform(post(PATH).with(user("family").roles("FAMILY")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM care_service_application", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_service_application");
        }
    }
}
