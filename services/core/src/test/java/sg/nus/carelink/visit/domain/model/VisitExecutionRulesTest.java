package sg.nus.carelink.visit.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VisitExecutionRulesTest {
    final LocalDateTime now=LocalDateTime.of(2026,10,7,12,0);
    final VisitExecutionPolicy policy=new VisitExecutionPolicy(Duration.ofMinutes(30),Duration.ofMinutes(10));
    Visit visit(Visit.Status state,Long node,String type,LocalDateTime end) { return new Visit(1L,2L,3L,node,null,type,now,end,null,null,state,now.plusMinutes(10),4L,0,null,null); }
    @Test void windowBoundariesLatenessAndDayEndAreExplicit() {
        var v=visit(Visit.Status.SCHEDULED,1L,"Task",now.plusHours(1));
        policy.requireWindow(v,now.minusMinutes(30));policy.requireWindow(v,now.plusHours(1));
        assertThatThrownBy(()->policy.requireWindow(v,now.minusMinutes(30).minusNanos(1))).hasMessageContaining("window");
        assertThatThrownBy(()->policy.requireWindow(v,now.plusHours(1).plusNanos(1))).hasMessageContaining("window");
        assertThat(policy.late(v,now.plusMinutes(10))).isFalse();assertThat(policy.late(v,now.plusMinutes(10).plusSeconds(1))).isTrue();
        assertThat(policy.closes(visit(Visit.Status.SCHEDULED,1L,"Task",null))).isEqualTo(now.toLocalDate().atTime(LocalTime.MAX));
    }
    @Test void stateObjectsEnforceTheWholeLifecycleAndUnknownServicesDoNotGuess() {
        var v=visit(Visit.Status.SCHEDULED,1L,"Manager task label",now.plusHours(1));
        var arrived=VisitStateFactory.forVisit(v).arrive(v,now);var started=VisitStateFactory.forVisit(arrived).start(arrived);
        assertThat(started.checkedInAt()).isEqualTo(now);assertThat(started.stateDeadline()).isNull();assertThat(started.checkedOutAt()).isNull();
        VisitStateFactory.forVisit(started).requireTaskResult();
        for(var status:Visit.Status.values()) {
            var candidate=visit(status,1L,"Task",now.plusHours(1));var state=VisitStateFactory.forVisit(candidate);
            if(status!=Visit.Status.SCHEDULED) assertThatThrownBy(()->state.arrive(candidate,now)).hasMessageContaining("cannot");
            if(status!=Visit.Status.ARRIVED) assertThatThrownBy(()->state.start(candidate)).hasMessageContaining("cannot");
            if(status!=Visit.Status.IN_PROGRESS) assertThatThrownBy(state::requireTaskResult).hasMessageContaining("cannot");
        }
        assertThatThrownBy(()->VisitStateFactory.forVisit(visit(Visit.Status.SCHEDULED,null,"UNKNOWN",null))).hasMessageContaining("strategy");
        assertThat(VisitStateFactory.forVisit(visit(Visit.Status.SCHEDULED,null,"A",null))).isNotNull();
        assertThatThrownBy(v::started).hasMessageContaining("not arrived");
        assertThatThrownBy(()->started.arrivedAt(now)).hasMessageContaining("not scheduled");
    }
    @Test void aStandaloneVisitRunsTheBasicStrategyOnItsServiceTask() {
        var extra=new Visit(1L,2L,3L,null,null,"Hospital escort",now,null,null,null,Visit.Status.SCHEDULED,null,null,0,null,null);
        assertThat(extra.standalone()).isTrue();
        var arrived=VisitStateFactory.forVisit(extra).arrive(extra,now);
        assertThat(arrived.status()).isEqualTo(Visit.Status.ARRIVED);
        var task=extra.standaloneTask();
        assertThat(task.visitId()).isEqualTo(1L);assertThat(task.carePlanNodeId()).isNull();
        assertThat(task.name()).isEqualTo("Hospital escort");assertThat(task.status()).isEqualTo(VisitTask.Status.PENDING);
        var unnamed=new Visit(1L,2L,3L,null,null," ",now,null,null,null,Visit.Status.SCHEDULED,null,null,0,null,null);
        assertThat(unnamed.standaloneTask().name()).isEqualTo("Visit");
        var planVisit=visit(Visit.Status.SCHEDULED,1L,"Task",null);
        assertThat(planVisit.standalone()).isFalse();
        assertThatThrownBy(planVisit::standaloneTask).isInstanceOf(IllegalStateException.class);
    }
    @Test void taskTerminalResultsHaveDifferentSemanticsAndCannotBeOverwritten() {
        var task=new VisitTask(1L,2L,3L,"Bathing",VisitTask.Status.PENDING,null,null,null);
        for(var result:new VisitTask.Status[]{VisitTask.Status.DONE,VisitTask.Status.SKIPPED,VisitTask.Status.REFUSED}) {
            var saved=task.result(result," fact "," reason ",now);
            assertThat(saved.outcome()).isEqualTo("fact");assertThat(saved.caregiverNote()).isEqualTo("reason");
            assertThat(saved.completedAt()).isEqualTo(result==VisitTask.Status.DONE?now:null);
            assertThatThrownBy(()->saved.result(result,null,null,now)).hasMessageContaining("cannot");
        }
        assertThat(task.result(VisitTask.Status.DONE,null,null,now).completedAt()).isEqualTo(now);
        assertThatThrownBy(()->task.result(VisitTask.Status.PENDING,null,null,now)).hasMessageContaining("cannot");
        assertThatThrownBy(()->task.result(null,null,null,now)).hasMessageContaining("cannot");
        assertThatThrownBy(()->task.result(VisitTask.Status.SKIPPED,null," ",now)).hasMessageContaining("reason");
        assertThatThrownBy(()->task.result(VisitTask.Status.REFUSED,null,null,now)).hasMessageContaining("reason");
        assertThatThrownBy(()->task.result(VisitTask.Status.DONE,"x".repeat(256),null,now)).hasMessageContaining("long");
        assertThatThrownBy(()->task.result(VisitTask.Status.DONE,null,"x".repeat(501),now)).hasMessageContaining("long");
    }
    @Test void gpsAndManualRecordsNeverMasqueradeAsEachOther() {
        var gps=new CheckInLocation("GPS",BigDecimal.valueOf(1.2),BigDecimal.valueOf(103.2),10.0,null,Instant.now());
        assertThat(gps.note()).isNull();
        assertThat(new CheckInLocation("MANUAL_LOCATION_NOTE",null,null,null," at doorway ",null).note()).isEqualTo("at doorway");
        assertThat(CheckInLocation.valid("GPS",BigDecimal.valueOf(91),BigDecimal.ZERO,10.0,null)).isFalse();
        assertThat(CheckInLocation.valid("GPS",BigDecimal.ZERO,BigDecimal.valueOf(181),10.0,null)).isFalse();
        assertThat(CheckInLocation.valid("GPS",BigDecimal.ZERO,BigDecimal.ZERO,Double.NaN,null)).isFalse();
        assertThat(CheckInLocation.valid("GPS",BigDecimal.ZERO,BigDecimal.ZERO,-1.0,null)).isFalse();
        assertThat(CheckInLocation.valid("GPS",null,null,null,null)).isFalse();
        assertThat(CheckInLocation.valid("GPS",BigDecimal.ZERO,BigDecimal.ZERO,0.0,"manual")).isFalse();
        assertThat(CheckInLocation.valid("MANUAL_LOCATION_NOTE",BigDecimal.ZERO,null,null,"note")).isFalse();
        assertThat(CheckInLocation.valid("MANUAL_LOCATION_NOTE",null,null,null," ")).isFalse();
        assertThat(CheckInLocation.valid("OTHER",null,null,null,"note")).isFalse();
        assertThatThrownBy(()->new CheckInLocation("GPS",null,null,null,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
