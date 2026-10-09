package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.testsupport.SharedMySql;

/** Exercises the real permission resolver, transactions and MySQL mapping in an isolated database. */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyElderProfileApiIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyElderProfileApiIT.class, null);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM elder_family_binding");
        jdbc.update("DELETE FROM elder");
        jdbc.update("DELETE FROM family_member");
        jdbc.update("DELETE FROM user_role");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("INSERT INTO app_user (id,username,password_hash,display_name) VALUES "
                + "(7,'family','{noop}test-password','Family'),(8,'other','{noop}test-password','Other')");
        jdbc.update("INSERT INTO user_role (user_id,role) VALUES (7,'FAMILY'),(8,'FAMILY')");
        jdbc.update("INSERT INTO family_member (id,user_id,full_name) VALUES (42,7,'Family'),(43,8,'Other')");
        jdbc.update("INSERT INTO elder (id,user_id,full_name,sector,medical_notes,continuity_preference) VALUES "
                + "(1,71,'Tan Mei','AMK','Internal clinical record','REQUIRED'),(2,72,'Other elder',NULL,NULL,'PREFERRED')");
        jdbc.update("INSERT INTO elder_family_binding (elder_id,family_member_id,status,access_scope) VALUES "
                + "(1,42,'ACTIVE','FULL'),(2,43,'ACTIVE','FULL')");
    }

    @Test
    void savesAndReadsBackWithoutCreatingAccountsOrOverwritingInternalFields() throws Exception {
        mvc.perform(put("/api/family/elders/1").with(user("family").roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"fullName":"Updated name","gender":"FEMALE","dateOfBirth":"1948-02-03",
                     "phone":"81234567","address":"12 Example Road","postalCode":"123456",
                     "preferredDialects":"Hokkien","livesAlone":true,"mobilityLevel":"ASSISTIVE_CANE"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated name"))
                .andExpect(jsonPath("$.dateOfBirth").value("1948-02-03"))
                .andExpect(jsonPath("$.postalCode").value("123456"))
                .andExpect(jsonPath("$.medicalNotes").doesNotExist()).andExpect(jsonPath("$.userId").doesNotExist());
        var saved = jdbc.queryForMap("SELECT * FROM elder WHERE id=1");
        assertThat(saved.get("user_id")).isEqualTo(71L);
        assertThat(saved.get("sector")).isEqualTo("AMK");
        assertThat(saved.get("medical_notes")).isEqualTo("Internal clinical record");
        assertThat(saved.get("continuity_preference")).isEqualTo("REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(2);
    }

    @Test
    void readOnlyBindingCannotPersistChanges() throws Exception {
        jdbc.update("UPDATE elder_family_binding SET access_scope='READ_ONLY' WHERE elder_id=1");
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessScope").value("READ_ONLY"));
        assertSaveForbidden("family", 1);
        assertThat(jdbc.queryForObject("SELECT full_name FROM elder WHERE id=1", String.class)).isEqualTo("Tan Mei");
    }

    @Test
    void listAndDetailExcludeAnotherFamilysElder() throws Exception {
        mvc.perform(get("/api/family/elders").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/family/elders/2").with(user("family").roles("FAMILY")))
                .andExpect(status().isForbidden());
        assertSaveForbidden("family", 2);
    }

    @Test
    void revocationRemovesListEntryAndBlocksPreviouslyOpenedProfile() throws Exception {
        mvc.perform(get("/api/family/elders/1").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk());
        jdbc.update("UPDATE elder_family_binding SET status='REVOKED' WHERE elder_id=1");
        mvc.perform(get("/api/family/elders").with(user("family").roles("FAMILY")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertSaveForbidden("family", 1);
    }

    private void assertSaveForbidden(String username, int id) throws Exception {
        mvc.perform(put("/api/family/elders/" + id).with(user(username).roles("FAMILY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Denied update\"}"))
                .andExpect(status().isForbidden());
    }
}
