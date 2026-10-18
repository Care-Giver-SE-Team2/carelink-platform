package sg.nus.carelink.profile.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.profile.application.*;
import sg.nus.carelink.profile.domain.repository.CredentialDocumentStorage.Upload;
import sg.nus.carelink.testsupport.SharedMySql;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL transactions, immutable file store and unchanged manager review. */
@SpringBootTest(properties={"carelink.caregiver.expiry-scan-cron=-", "carelink.missed-check-in.enabled=false",
        "carelink.roster.schedule-cron=-", "carelink.report.schedule-cron=-"})
@AutoConfigureMockMvc
@Import(CaregiverCredentialSubmissionIT.FixedClock.class)
class CaregiverCredentialSubmissionIT {
    static final Path ROOT = Path.of("../reports/main-sync/credential-it-" + UUID.randomUUID()).toAbsolutePath().normalize();
    static final long CG=50101, USER=50001, TYPE=50201;
    static final JsonMapper JSON=JsonMapper.builder().build();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry,CaregiverCredentialSubmissionIT.class,"+05:00");
        registry.add("carelink.caregiver.credential-storage",ROOT::toString);
    }
    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock clock() { return Clock.fixed(Instant.parse("2026-10-10T17:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CaregiverCredentialService service;
    @Autowired CaregiverWorkDirectory directory;

    @BeforeEach void seed() {
        jdbc.update("delete from credential_submission"); jdbc.update("delete from credential_document");
        jdbc.update("update credential set renews_credential_id=null"); jdbc.update("delete from credential");
        jdbc.update("delete from audit_log where resource_type='CREDENTIAL'");
        for (int i=1;i<=4;i++) {
            String name=List.of("cert-a","cert-b","cert-manager","cert-family").get(i-1);
            jdbc.update("insert into app_user(id,username,password_hash,display_name,enabled) values (?,?,'{noop}test-password',?,true) on duplicate key update enabled=true",50000+i,name,name);
            jdbc.update("insert ignore into user_role(user_id,role) values (?,?)",50000+i,i==3 ? "MANAGER" : i==4 ? "FAMILY" : "CAREGIVER");
        }
        jdbc.update("insert into caregiver(id,user_id,full_name,status) values (?,?,'Demo A','ONBOARDING') on duplicate key update status='ONBOARDING'",CG,USER);
        jdbc.update("insert ignore into caregiver(id,user_id,full_name,status) values (50102,50002,'Demo B','AVAILABLE')");
        jdbc.update("insert ignore into credential_type(id,name) values (?,'IT First aid')",TYPE);
    }
    @AfterAll static void cleanupFiles() throws Exception {
        if (!ROOT.startsWith(Path.of("../reports/main-sync").toAbsolutePath().normalize())) throw new IllegalStateException();
        if (Files.exists(ROOT)) try(var paths=Files.walk(ROOT)) {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    static byte[] png() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var output=new MemoryCacheImageOutputStream(bytes)) { ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output); }
        return bytes.toByteArray();
    }
    long upload(String path,String key) throws Exception {
        var response=mvc.perform(multipart(path).file(new MockMultipartFile("files","demo.png","image/png",png()))
                .param("credentialTypeId",String.valueOf(TYPE)).param("certificateNo","DEMO-N").param("issuingBody","Demo Institution")
                .param("validFrom","2026-10-11").param("expiryDate","2027-10-11").param("clientRequestId",key)
                .with(user("cert-a").roles("CAREGIVER")).with(csrf())).andExpect(status().isOk()).andReturn();
        return JSON.readTree(response.getResponse().getContentAsString()).path("credentialId").asLong();
    }
    long old() {
        jdbc.update("insert into credential(id,caregiver_id,credential_type_id,certificate_no,valid_from,expiry_date,status) values (50301,?,?,'OLD','2026-01-01','2026-10-15','PUBLISHED')",CG,TYPE);
        return 50301;
    }
    @Test void ownUploadIsImmutablePendingAndManagerReadsRealAttachment() throws Exception {
        String key=UUID.randomUUID().toString(); long id=upload("/api/caregivers/me/credentials",key);
        assertThat(upload("/api/caregivers/me/credentials",key)).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from credential",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from credential where id=?",String.class,id)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("select status from caregiver where id=?",String.class,CG)).isEqualTo("ONBOARDING");
        assertThat(jdbc.queryForObject("select submitted_at from credential_submission where credential_id=?",LocalDateTime.class,id))
                .isEqualTo(LocalDateTime.of(2026,10,11,1,0));
        mvc.perform(get("/api/caregivers/me/credential-types").with(user("cert-a").roles("CAREGIVER"))).andExpect(status().isOk());
        mvc.perform(get("/api/caregivers/me/credentials").with(user("cert-a").roles("CAREGIVER")))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].documents[0].fileName").value("demo.png"));
        var listing=mvc.perform(get("/api/credentials/{id}/documents",id).with(user("cert-manager").roles("MANAGER")))
                .andExpect(status().isOk()).andReturn();
        long document=JSON.readTree(listing.getResponse().getContentAsString()).get(0).path("id").asLong();
        mvc.perform(get("/api/credentials/{id}/documents/{doc}/content",id,document).with(user("cert-manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/caregivers/me/credentials/{id}",id).with(user("cert-b").roles("CAREGIVER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/credentials/{id}/documents",id).with(user("cert-b").roles("CAREGIVER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/credentials/{id}/documents",id).with(user("cert-family").roles("FAMILY"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/credentials/{id}/documents/{doc}/content",id,document+999).with(user("cert-a").roles("CAREGIVER"))).andExpect(status().isNotFound());
    }
    @Test void rejectionAndResubmissionRetainHistoryWithoutFalseAlertFork() throws Exception {
        long old=old(); long first=upload("/api/caregivers/me/credentials/"+old+"/renewals",UUID.randomUUID().toString());
        assertThat(directory.alerts(CG,LocalDate.of(2026,10,11)).items().getFirst().renewalState()).isEqualTo("PENDING_REVIEW");
        mvc.perform(post("/api/credentials/{id}/reject",first).contentType("application/json").content("{\"reason\":\"Please use a readable copy\"}")
                .with(user("cert-manager").roles("MANAGER")).with(csrf())).andExpect(status().isNoContent());
        long second=upload("/api/caregivers/me/credentials/"+first+"/resubmissions",UUID.randomUUID().toString());
        assertThat(jdbc.queryForObject("select status from credential where id=?",String.class,first)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select count(*) from credential_document where credential_id=?",Long.class,first)).isEqualTo(1);
        var alerts=directory.alerts(CG,LocalDate.of(2026,10,11));
        assertThat(alerts.context().reviewRequired()).isFalse();
        assertThat(alerts.items().getFirst().renewalState()).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("select renews_credential_id from credential where id=?",Long.class,second)).isEqualTo(old);
        mvc.perform(post("/api/credentials/{id}/publish",second).with(user("cert-manager").roles("MANAGER")).with(csrf())).andExpect(status().isNoContent());
        assertThat(directory.alerts(CG,LocalDate.of(2026,10,11)).items()).isEmpty();
    }
    @Test void pendingDifferentPayloadDuplicateAndCsrfAreRejected() throws Exception {
        String key=UUID.randomUUID().toString(); upload("/api/caregivers/me/credentials",key);
        mvc.perform(multipart("/api/caregivers/me/credentials").file(new MockMultipartFile("files","demo.png","image/png",png()))
                .param("issuingBody","Other").param("validFrom","2026-10-11").param("expiryDate","2027-10-11").param("clientRequestId",key)
                .with(user("cert-a").roles("CAREGIVER")).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_KEY_REUSED"));
        assertThatThrownBy(() -> service.submit("cert-a","NEW",null,request(UUID.randomUUID().toString()),List.of(new Upload("demo.png","image/png",png()))))
                .hasMessageContaining("awaiting review");
        mvc.perform(multipart("/api/caregivers/me/credentials").file(new MockMultipartFile("files",new byte[]{1}))
                .with(user("cert-a").roles("CAREGIVER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/caregivers/me/credentials")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/caregivers/me/credentials?page=-1").with(user("cert-a").roles("CAREGIVER"))).andExpect(status().isBadRequest());
    }
    CaregiverCredentialService.Request request(String key) {
        return new CaregiverCredentialService.Request(TYPE,"N","Demo",LocalDate.of(2026,10,11),LocalDate.of(2027,10,11),false,key);
    }
    @Test void concurrentFirstRequestsProduceOnePendingSubmission() throws Exception {
        byte[] bytes=png(); var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var work=(Callable<Boolean>) () -> { barrier.await(); try {
                service.submit("cert-a","NEW",null,request(UUID.randomUUID().toString()),List.of(new Upload("demo.png","image/png",bytes))); return true;
            } catch(sg.nus.carelink.shared.error.BusinessRuleViolation ex) { return false; } };
            var first=executor.submit(work); var second=executor.submit(work);
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("select count(*) from credential where status='SUBMITTED'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from credential_document",Long.class)).isEqualTo(1);
    }
    @Test void databaseFailureRollsBackFactsAndNewFiles() throws Exception {
        Files.createDirectories(ROOT); long before;
        try(var paths=Files.list(ROOT)) { before=paths.count(); }
        jdbc.execute("create trigger credential_test_fail before insert on credential_submission for each row signal sqlstate '45000' set message_text='test rollback'");
        try {
            assertThatThrownBy(() -> service.submit("cert-a","NEW",null,request(UUID.randomUUID().toString()),List.of(new Upload("demo.png","image/png",png()))))
                    .isInstanceOf(RuntimeException.class);
        } finally { jdbc.execute("drop trigger credential_test_fail"); }
        assertThat(jdbc.queryForObject("select count(*) from credential",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from credential_document",Long.class)).isZero();
        try(var paths=Files.list(ROOT)) { assertThat(paths.count()).isEqualTo(before); }
    }
    @Test void permanentFutureStartAndDisabledAccountsKeepTheirMeaning() throws Exception {
        var permanent=new CaregiverCredentialService.Request(TYPE,null,"Demo",LocalDate.of(2026,11,1),null,true,UUID.randomUUID().toString());
        long id=service.submit("cert-a","NEW",null,permanent,List.of(new Upload("demo.png","image/png",png()))).credentialId();
        assertThat(service.detail("cert-a",id).expiryDate()).isNull();
        mvc.perform(post("/api/credentials/{id}/publish",id).with(user("cert-manager").roles("MANAGER")).with(csrf())).andExpect(status().isNoContent());
        assertThat(directory.alerts(CG,LocalDate.of(2026,10,11)).items()).isEmpty();
        jdbc.update("update app_user set enabled=false where id=?",USER);
        assertThatThrownBy(() -> service.detail("cert-a",id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void legacyRecordsHaveNoDocumentsAndRenewalNeverModifiesParent() throws Exception {
        long id=old(); assertThat(service.detail("cert-a",id).documents()).isEmpty();
        assertThatThrownBy(() -> service.submit("cert-a","NEW",null,request(UUID.randomUUID().toString()),List.of(new Upload("demo.png","image/png",png()))))
                .hasMessageContaining("Renew certificate");
        long child=upload("/api/caregivers/me/credentials/"+id+"/renewals",UUID.randomUUID().toString());
        assertThat(jdbc.queryForObject("select status from credential where id=?",String.class,id)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("select renews_credential_id from credential where id=?",Long.class,child)).isEqualTo(id);
        jdbc.update("update caregiver set status='INACTIVE' where id=?",CG);
        assertThatThrownBy(() -> service.submit("cert-a","NEW",null,request(UUID.randomUUID().toString()),List.of(new Upload("demo.png","image/png",png()))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void nullAndLiteralNullAreDifferentRequestsAndOversizeYearsFailBeforeWriting() throws Exception {
        String key = UUID.randomUUID().toString();
        var empty = new CaregiverCredentialService.Request(TYPE,null,"Demo",LocalDate.of(2026,10,11),LocalDate.of(2027,10,11),false,key);
        var literal = new CaregiverCredentialService.Request(TYPE,"null","Demo",empty.validFrom(),empty.expiryDate(),false,key);
        var files = List.of(new Upload("demo.png","image/png",png()));
        service.submit("cert-a","NEW",null,empty,files);
        assertThatThrownBy(() -> service.submit("cert-a","NEW",null,literal,files)).hasMessageContaining("different submission");
        var badYear = new CaregiverCredentialService.Request(TYPE,null,"Demo",empty.validFrom(),LocalDate.of(10000,1,1),false,UUID.randomUUID().toString());
        assertThatThrownBy(() -> service.submit("cert-a","NEW",null,badYear,files)).hasMessageContaining("expiry year");
        assertThat(jdbc.queryForObject("select count(*) from credential",Long.class)).isEqualTo(1);
    }

    @Test void rejectedFirstSubmissionCannotBypassAnApprovedCredential() throws Exception {
        old();
        jdbc.update("insert into credential(id,caregiver_id,credential_type_id,expiry_date,status) values (50302,?,?,'2027-10-11','REJECTED')",CG,TYPE);
        assertThatThrownBy(() -> service.submit("cert-a","RESUBMIT",50302L,request(UUID.randomUUID().toString()),List.of(new Upload("demo.png","image/png",png()))))
                .hasMessageContaining("Renew certificate");
    }

    @Test void invalidGraphsAndUnlinkedRetryBranchesRequireReview() throws Exception {
        old();
        jdbc.update("insert into credential(id,caregiver_id,credential_type_id,valid_from,expiry_date,status,renews_credential_id) values (50302,?,?,'2026-01-01','2027-10-11','REJECTED',50301)",CG,TYPE);
        var files = List.of(new Upload("demo.png","image/png",png()));
        assertThatThrownBy(() -> service.submit("cert-a","RENEWAL",50301L,request(UUID.randomUUID().toString()),files))
                .hasMessageContaining("resubmitted or reviewed");
        jdbc.update("update credential set status='PUBLISHED' where id=50302");
        jdbc.update("update credential set renews_credential_id=50302 where id=50301");
        assertThatThrownBy(() -> service.submit("cert-a","RENEWAL",50301L,request(UUID.randomUUID().toString()),files))
                .hasMessageContaining("chain needs");
        assertThat(jdbc.queryForObject("select count(*) from credential_document",Long.class)).isZero();
    }

    @Test void concurrentIdenticalKeysReturnSameFactAndAuditFailureRollsBack() throws Exception {
        String key = UUID.randomUUID().toString(); var req=request(key); byte[] bytes=png(); var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Long> work=() -> { barrier.await(); return service.submit("cert-a","NEW",null,req,List.of(new Upload("demo.png","image/png",bytes))).credentialId(); };
            var first=executor.submit(work); var second=executor.submit(work);
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from credential_document",Long.class)).isEqualTo(1);
        jdbc.update("insert into credential_type(id,name) values (50202,'IT Other')");
        long before; try(var paths=Files.list(ROOT)) { before=paths.count(); }
        jdbc.execute("create trigger credential_audit_fail before insert on audit_log for each row signal sqlstate '45000' set message_text='test audit rollback'");
        try {
            var other = new CaregiverCredentialService.Request(50202L,"N","Demo",req.validFrom(),req.expiryDate(),false,UUID.randomUUID().toString());
            assertThatThrownBy(() -> service.submit("cert-a","NEW",null,other,List.of(new Upload("demo.png","image/png",bytes)))).isInstanceOf(RuntimeException.class);
        } finally { jdbc.execute("drop trigger credential_audit_fail"); }
        assertThat(jdbc.queryForObject("select count(*) from credential",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from credential_submission",Long.class)).isEqualTo(1);
        try(var paths=Files.list(ROOT)) { assertThat(paths.count()).isEqualTo(before); }
    }

    @Test void futureRenewalDoesNotHideOldRecordAndTamperedDocumentCannotBeDownloaded() throws Exception {
        old(); var req = new CaregiverCredentialService.Request(TYPE,"N","Demo",LocalDate.of(2026,11,1),LocalDate.of(2027,11,1),false,UUID.randomUUID().toString());
        long id=service.submit("cert-a","RENEWAL",50301L,req,List.of(new Upload("demo.png","image/png",png()))).credentialId();
        mvc.perform(post("/api/credentials/{id}/publish",id).with(user("cert-manager").roles("MANAGER")).with(csrf())).andExpect(status().isNoContent());
        assertThat(service.detail("cert-a",50301L).historical()).isFalse();
        assertThat(directory.alerts(CG,LocalDate.of(2026,10,11)).items().getFirst().renewalState()).isEqualTo("APPROVED_NOT_EFFECTIVE");
        long document=service.detail("cert-a",id).documents().getFirst().id();
        jdbc.update("update credential_document set sha256=repeat('0',64) where id=?",document);
        mvc.perform(get("/api/credentials/{id}/documents/{doc}/content",id,document).with(user("cert-a").roles("CAREGIVER")))
                .andExpect(status().isServiceUnavailable());
    }
}
