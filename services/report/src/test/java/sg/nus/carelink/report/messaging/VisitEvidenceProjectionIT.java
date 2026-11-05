package sg.nus.carelink.report.messaging;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.nus.carelink.events.*;
import sg.nus.carelink.eventtypes.*;
import sg.nus.carelink.testsupport.SharedMySql;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
class VisitEvidenceProjectionIT {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){SharedMySql.register(r,VisitEvidenceProjectionIT.class,null,"connectionTimeZone=Asia/Singapore");}
 @Autowired List<EventHandler<?>> handlers; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
 final OffsetDateTime at=OffsetDateTime.parse("2026-10-19T09:00:00+08:00");
 @BeforeEach void clear(){jdbc.update("delete from report_visit_evidence where visit_id=89001");jdbc.update("delete from report_visit where visit_id=89001");}
 @SuppressWarnings("unchecked") void deliver(VisitEvidenceUpdated e){var h=(EventHandler<VisitEvidenceUpdated>)handlers.stream().filter(x->x.type().equals(VisitEvidenceUpdated.TYPE)).findFirst().orElseThrow();new TransactionTemplate(manager).executeWithoutResult(_->h.handle(e,new EventMetadata("test",VisitEvidenceUpdated.TYPE,"visit",at.toInstant(),1L)));}
 VisitEvidenceUpdated snapshot(int version,int count,int verified){return new VisitEvidenceUpdated(89001L,88001L,at,version,count,verified);}
 @Test void duplicatesAndOlderSnapshotsDoNotInflateOrRegressCounts(){deliver(snapshot(4,2,0));deliver(snapshot(4,2,0));deliver(snapshot(2,1,0));assertThat(jdbc.queryForObject("select evidence_count from report_visit_evidence where visit_id=89001",Integer.class)).isEqualTo(2);assertThat(jdbc.queryForObject("select verified_evidence_count from report_visit_evidence where visit_id=89001",Integer.class)).isZero();}
 @Test void evidenceBeforeScheduleCreatesPlaceholderWithoutInventingExecutionState(){deliver(snapshot(3,1,0));assertThat(jdbc.queryForObject("select count(*) from report_visit where visit_id=89001",Integer.class)).isZero();assertThat(jdbc.queryForObject("select version from report_visit_evidence where visit_id=89001",Integer.class)).isEqualTo(3);}
 @Test void lateEvidenceDoesNotRewindCompletedVisit(){deliver(snapshot(2,1,0));jdbc.update("insert into report_visit(visit_id,elder_id,scheduled_start,status,version) values (89001,88001,?,'COMPLETED',9)",java.sql.Timestamp.valueOf(at.toLocalDateTime()));deliver(snapshot(5,3,0));assertThat(jdbc.queryForObject("select status from report_visit where visit_id=89001",String.class)).isEqualTo("COMPLETED");assertThat(jdbc.queryForObject("select version from report_visit where visit_id=89001",Integer.class)).isEqualTo(9);assertThat(jdbc.queryForObject("select evidence_count from report_visit_evidence where visit_id=89001",Integer.class)).isEqualTo(3);}
 @Test void failedConsumerTransactionRollsBackProjection(){new TransactionTemplate(manager).executeWithoutResult(tx->{deliver(snapshot(2,1,0));tx.setRollbackOnly();});assertThat(jdbc.queryForObject("select count(*) from report_visit_evidence where visit_id=89001",Integer.class)).isZero();}
}
