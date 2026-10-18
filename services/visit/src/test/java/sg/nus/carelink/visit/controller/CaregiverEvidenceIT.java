package sg.nus.carelink.visit.controller;

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
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.application.*;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.VisitEvidenceFileStore;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"carelink.caregiver.expiry-scan-cron=-","carelink.missed-check-in.enabled=false","carelink.roster.schedule-cron=-","carelink.report.schedule-cron=-","carelink.caregiver.evidence-max-files=2"})
@AutoConfigureMockMvc
@Import(CaregiverEvidenceIT.FixedClock.class)
class CaregiverEvidenceIT {
    static final Path ROOT=Path.of("target","private-test-files","evidence-it-"+UUID.randomUUID()).toAbsolutePath().normalize();
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { SharedMySql.register(r,CaregiverEvidenceIT.class,"+05:00","connectionTimeZone=Asia/Singapore"); r.add("carelink.caregiver.evidence-storage",ROOT::toString); }
    @TestConfiguration static class FixedClock { @Bean @Primary Clock clock() { return Clock.fixed(Instant.parse("2026-10-12T01:00:00Z"),ZoneId.of("Asia/Singapore")); } }
    @Autowired CaregiverEvidenceService service;
    @Autowired CaregiverVisitExecutionService execution;
    @Autowired CaregiverHistoryService history;
    @Autowired CaregiverHealthService health;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoSpyBean VisitEvidenceFileStore metadata;
    @MockitoSpyBean sg.nus.carelink.visit.domain.repository.CaregiverCommandStore receipts;
    @MockitoSpyBean sg.nus.carelink.visit.domain.repository.EvidenceImageStorage images;
    long visit,caregiver,user;
    String username;
    byte[] image;
    @BeforeEach void setup() throws Exception {
        reset(metadata,receipts,images); username="evidence-"+UUID.randomUUID();
        user=insert("insert into app_user(username,password_hash,display_name,enabled) values (?,'{noop}test-password','Fictional caregiver',true)",username);
        jdbc.update("insert into user_role(user_id,role) values (?,'CAREGIVER')",user);
        caregiver=insert("insert into caregiver(user_id,full_name,status) values (?,'Fictional caregiver','AVAILABLE')",user);
        long elder=insert("insert into elder(full_name) values ('Fictional elder')");
        // Match the production JDBC/JPA date representation even when the JVM is UTC.
        visit=insert("insert into visit(elder_id,caregiver_id,service_type,scheduled_start,scheduled_end,status) values (?,?,'EXTRA_SERVICE',?,?,'SCHEDULED')",elder,caregiver,
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026,10,12,9,0)),
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026,10,12,10,0)));
        var bytes=new ByteArrayOutputStream(); try(var out=new MemoryCacheImageOutputStream(bytes)) { ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out); } image=bytes.toByteArray();
    }
    private long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder(); jdbc.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();
    }
    private int checkIn() { return execution.checkIn(username,visit,0,UUID.randomUUID(),new CheckInLocation("MANUAL_LOCATION_NOTE",null,null,null,"Fictional doorway",null)).visitVersion(); }
    private CaregiverEvidenceService.Saved upload(int version,UUID key) { return service.upload(username,visit,VisitEvidence.Kind.PHOTO,null,version,key,"demo.png","image/png",image); }
    @Test void uploadsUnverifiedImageAndDownloadsPrivatelyWithoutBlockingCheckout() throws Exception {
        var saved=upload(checkIn(),UUID.randomUUID());
        assertThat(saved.evidence().verificationStatus()).isEqualTo("UNVERIFIED"); assertThat(saved.evidence().available()).isTrue();
        assertThat(saved.evidence().capturedAt()).isEqualTo(LocalDateTime.of(2026,10,12,9,0));
        assertThat(service.content(username,visit,saved.evidence().id()).bytes()).isNotEmpty();
        mvc.perform(get(saved.evidence().downloadPath()).with(user(username).roles("CAREGIVER"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        execution.checkOut(username,visit,saved.visitVersion(),UUID.randomUUID());
        assertThat(service.content(username,visit,saved.evidence().id()).bytes()).isNotEmpty();
        assertThatThrownBy(()->upload(saved.visitVersion()+1,UUID.randomUUID())).hasMessageContaining("before check-out");
        assertThat(jdbc.queryForObject("select status from visit_task where visit_id=?",String.class,visit)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select verification_status from visit_evidence where id=?",String.class,saved.evidence().id())).isEqualTo("UNVERIFIED");
    }
    @Test void replayKeepsOriginalIdTimeAndVersionAndChangedContentConflicts() {
        int version=checkIn(); var key=UUID.randomUUID(); var first=upload(version,key); var replay=upload(version,key);
        assertThat(replay.evidence()).isEqualTo(first.evidence()); assertThat(replay.replayed()).isTrue();
        assertThat(replay.visitVersion()).isEqualTo(first.visitVersion());
        assertThatThrownBy(()->service.upload(username,visit,VisitEvidence.Kind.SIGNATURE,null,version,key,"demo.png","image/png",image)).hasMessageContaining("different content");
        assertThat(service.list(username,visit,0,20).total()).isEqualTo(1);
    }
    @Test void limitsAreCheckedInsideVisitLockAndWrongTasksCannotBeLinked() {
        int version=checkIn();
        assertThatThrownBy(()->service.upload(username,visit,VisitEvidence.Kind.PHOTO,999999L,version,UUID.randomUUID(),"demo.png","image/png",image)).hasMessageContaining("does not belong");
        var first=upload(version,UUID.randomUUID()); var second=upload(first.visitVersion(),UUID.randomUUID());
        assertThatThrownBy(()->upload(second.visitVersion(),UUID.randomUUID())).hasMessageContaining("file limit");
        assertThat(service.list(username,visit,0,20).total()).isEqualTo(2);
    }
    @Test void metadataFailureRollsBackFileEvidenceAndParentVersion() throws Exception {
        int version=checkIn(); long before=files();
        doThrow(new IllegalStateException("Synthetic metadata failure")).when(metadata).save(any());
        assertThatThrownBy(()->upload(version,UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        assertThat(files()).isEqualTo(before); assertThat(service.list(username,visit,0,20).total()).isZero();
        assertThat(jdbc.queryForObject("select version from visit where id=?",Integer.class,visit)).isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt where visit_id=? and action_code='EVIDENCE_UPLOAD'",Long.class,visit)).isZero();
    }
    @Test void missingFileOrHashMismatchDoesNotReturnContent() throws Exception {
        var saved=upload(checkIn(),UUID.randomUUID());
        String key=jdbc.queryForObject("select storage_key from visit_evidence_file where evidence_id=?",String.class,saved.evidence().id());
        // Alter only this test's generated file, to simulate corruption in private storage.
        Files.write(ROOT.resolve(key),new byte[]{1});
        assertThatThrownBy(()->service.content(username,visit,saved.evidence().id())).hasMessageContaining("unavailable");
    }
    @Test void legacyReferenceNeverBecomesAFilePath() {
        jdbc.update("insert into visit_evidence(visit_id,kind,reference,verification_status,captured_at) values (?,'PHOTO','../../private','UNVERIFIED','2026-10-12 09:00:00')",visit);
        var old=service.list(username,visit,0,20).items().getFirst();
        assertThat(old.available()).isFalse(); assertThat(old.downloadPath()).isNull();
        assertThatThrownBy(()->service.content(username,visit,old.id())).isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
    }
    @Test void csrfForeignIdentityAndUnsupportedKindAreRejected() throws Exception {
        checkIn(); var file=new MockMultipartFile("file","demo.png","image/png",image);
        mvc.perform(multipart("/api/visits/"+visit+"/evidence").file(file).with(user(username).roles("CAREGIVER")).param("kind","PHOTO").param("expectedVersion","1").param("clientRequestId",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/"+visit+"/evidence")).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->service.upload(username,visit,VisitEvidence.Kind.READING,null,1,UUID.randomUUID(),"demo.png","image/png",image)).hasMessageContaining("PHOTO");
        jdbc.update("update caregiver set status='ONBOARDING' where id=?",caregiver);
        assertThatThrownBy(()->upload(1,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void ownMonthlyHistoryAggregatesRealCommandsAndHealthWithoutElderIdentity() throws Exception {
        int version=checkIn();
        var measured=health.record(username,visit,version,UUID.randomUUID(),new HealthMeasurement(new java.math.BigDecimal("120"),new java.math.BigDecimal("80"),new java.math.BigDecimal("72"),new java.math.BigDecimal("36.5"),HealthObservation.Flag.NO_CONCERN,null));
        var saved=upload(measured.visitVersion(),UUID.randomUUID()); execution.checkOut(username,visit,saved.visitVersion(),UUID.randomUUID());
        jdbc.update("update caregiver_command_receipt set occurred_at='2026-10-12 09:00:00.123456' where visit_id=? and action_code='EVIDENCE_UPLOAD'",visit);
        var month=history.own(username,"2026-10","COMPLETED",0,20);
        assertThat(month.total()).isEqualTo(1); assertThat(month.items().getFirst().ownCheckInAt()).isEqualTo(LocalDateTime.of(2026,10,12,9,0));
        assertThat(month.items().getFirst().ownCheckOutAt()).isNotNull();
        var detail=history.detail(username,visit,0,20);
        assertThat(detail.facts().getFirst().action()).isEqualTo("CHECK_OUT");
        assertThat(detail.facts()).extracting(CaregiverHistoryService.Fact::action).contains("CHECK_IN","HEALTH_RECORD","EVIDENCE_UPLOAD","CHECK_OUT");
        assertThat(detail.facts().stream().filter(f->f.health()!=null).findFirst().orElseThrow().health().readings()).hasSize(4);
        assertThat(history.own(username,"2026-09",null,0,20).total()).isZero();
        mvc.perform(get("/api/caregivers/me/history/"+visit).with(user(username).roles("CAREGIVER"))).andExpect(status().isOk()).andExpect(jsonPath("$.elderId").doesNotExist());
        assertThatThrownBy(()->history.own(username,"not-a-month",null,0,20)).hasMessageContaining("YYYY-MM");
    }
    @Test void historicalUploadRightsUseTheActualUploaderNotCurrentAssignment() throws Exception {
        var saved=upload(checkIn(),UUID.randomUUID());
        long otherUser=insert("insert into app_user(username,password_hash,display_name,enabled) values (?,'{noop}test','Other',true)",username+"other");
        jdbc.update("insert into user_role(user_id,role) values (?,'CAREGIVER')",otherUser);
        long other=insert("insert into caregiver(user_id,full_name,status) values (?,'Other','AVAILABLE')",otherUser);
        // Adversarial current-assignment change: history rights must not follow this field.
        jdbc.update("update visit set caregiver_id=? where id=?",other,visit);
        assertThat(history.detail(username,visit,0,20).total()).isEqualTo(2);
        assertThat(service.historicalContent(username,visit,saved.evidence().id()).bytes()).isNotEmpty();
        assertThatThrownBy(()->service.content(username,visit,saved.evidence().id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->history.detail(username+"other",visit,0,20)).isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
        assertThatThrownBy(()->service.historicalContent(username+"other",visit,saved.evidence().id())).isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
        jdbc.update("update caregiver set status='INACTIVE' where id=?",caregiver);
        assertThatThrownBy(()->history.detail(username,visit,0,20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void legacyAssignedVisitWithoutActorFactsDoesNotInventPersonalHistory() {
        assertThat(history.own(username,"2026-10",null,0,20).total()).isZero();
        assertThatThrownBy(()->history.detail(username,visit,0,20)).isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
    }
    @Test void inactiveAccountCannotBypassHistoryPolicyWithCurrentAssignmentDownload() {
        var saved=upload(checkIn(),UUID.randomUUID());
        jdbc.update("update caregiver set status='INACTIVE' where id=?",caregiver);
        assertThatThrownBy(()->service.content(username,visit,saved.evidence().id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.list(username,visit,0,20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.historicalContent(username,visit,saved.evidence().id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void realMultipartUploadReturnsPrivateMetadataAndNeverAStorageKey() throws Exception {
        int version=checkIn();
        mvc.perform(multipart("/api/visits/"+visit+"/evidence").file(new MockMultipartFile("file","demo.png","image/png",image))
                .with(user(username).roles("CAREGIVER")).with(csrf()).param("kind","PHOTO")
                .param("expectedVersion",String.valueOf(version)).param("clientRequestId",UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.evidence.storageKey").doesNotExist()).andExpect(jsonPath("$.visitVersion").value(version+1));
    }
    @Test void receiptFailureAlsoRollsBackMetadataFileAndVersion() throws Exception {
        int version=checkIn(); long before=files();
        doThrow(new IllegalStateException("Synthetic receipt failure")).when(receipts).save(any());
        assertThatThrownBy(()->upload(version,UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        assertThat(files()).isEqualTo(before); assertThat(metadata.count(visit)).isZero();
        assertThat(jdbc.queryForObject("select version from visit where id=?",Integer.class,visit)).isEqualTo(version);
    }
    @Test void concurrentUploadAndCheckoutCommitOnlyOneVersionThenCheckoutCanFinish() throws Exception {
        int version=checkIn();
        var result=race(()->upload(version,UUID.randomUUID()),()->execution.checkOut(username,visit,version,UUID.randomUUID()));
        assertThat(result).containsExactlyInAnyOrder(true,false);
        int current=jdbc.queryForObject("select version from visit where id=?",Integer.class,visit);
        assertThat(current).isEqualTo(version+1);
        if("IN_PROGRESS".equals(jdbc.queryForObject("select status from visit where id=?",String.class,visit))) execution.checkOut(username,visit,current,UUID.randomUUID());
        assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("COMPLETED");
    }
    @Test void concurrentUploadsCannotExceedCountOrLoseVersion() throws Exception {
        var first=upload(checkIn(),UUID.randomUUID()); int version=first.visitVersion();
        assertThat(race(()->upload(version,UUID.randomUUID()),()->upload(version,UUID.randomUUID()))).containsExactlyInAnyOrder(true,false);
        assertThat(metadata.fileCount(visit)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select version from visit where id=?",Integer.class,visit)).isEqualTo(version+1);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"CANCELLED","EXCEPTION","REASSIGNED"})
    void stateAndAssignmentAreRecheckedAfterPreparingTheImage(String change) throws Exception {
        int version=checkIn(); long before=files();
        var prepared=new CountDownLatch(1); var proceed=new CountDownLatch(1);
        doAnswer(call->{var result=call.callRealMethod(); prepared.countDown(); assertThat(proceed.await(10,TimeUnit.SECONDS)).isTrue(); return result;})
                .when(images).prepare(anyString(),anyString(),any());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var command=pool.submit(()->upload(version,UUID.randomUUID()));
            try {
                assertThat(prepared.await(10,TimeUnit.SECONDS)).isTrue();
                // Simulates a competing committed command before the upload acquires the Visit lock.
                if("REASSIGNED".equals(change)) jdbc.update("update visit set caregiver_id=null,version=version+1 where id=?",visit);
                else jdbc.update("update visit set status=?,version=version+1 where id=?",change,visit);
            } finally { proceed.countDown(); }
            assertThatThrownBy(()->command.get(20,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .satisfies(error->assertThat(error.getCause()).isInstanceOfAny(sg.nus.carelink.shared.error.BusinessRuleViolation.class,org.springframework.security.access.AccessDeniedException.class));
        }
        assertThat(files()).isEqualTo(before); assertThat(metadata.count(visit)).isZero();
    }
    private List<Boolean> race(Callable<?> a,Callable<?> b) throws Exception {
        var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var results=new ArrayList<Future<Boolean>>();
            for(var command:List.of(a,b)) results.add(pool.submit(()->{ready.countDown(); assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();
                try { command.call(); return true; } catch(sg.nus.carelink.shared.error.BusinessRuleViolation _) { return false; }}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            return List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));
        }
    }
    private long files() throws Exception { if(!Files.exists(ROOT)) return 0; try(var files=Files.list(ROOT)) { return files.count(); } }
}
