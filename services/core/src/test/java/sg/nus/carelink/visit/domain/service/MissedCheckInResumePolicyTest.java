package sg.nus.carelink.visit.domain.service;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.visit.domain.model.*;

class MissedCheckInResumePolicyTest {
    private final LocalDateTime now=LocalDateTime.of(2026,10,9,10,15,1);
    private final MissedCheckInTrigger trigger=new MissedCheckInTrigger(1L,11L,3L,now.minusMinutes(15),now.minusSeconds(1),0,now);
    private Visit visit(Visit.Status status,Long caregiver,Long absence,Integer version) {
        return new Visit(1L,2L,caregiver,4L,absence,"Task",trigger.scheduledStart(),now.plusHours(1),null,null,status,null,5L,version,null,null);
    }
    private VisitStateTransition transition(String from,Long actor) {
        return new VisitStateTransition(1L,1L,from,"EXCEPTION",actor,VisitStateTransition.Result.APPLIED,null,now);
    }
    @Test void ledgerVersionAssignmentAndSoleSystemHistoryMustAllAgree() {
        var v=visit(Visit.Status.EXCEPTION,3L,null,1);var history=List.of(transition("SCHEDULED",null));
        assertThat(MissedCheckInResumePolicy.permits(v,trigger,history,true)).isTrue();
        assertThat(MissedCheckInResumePolicy.permits(v,trigger,history,false)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(v,null,history,true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(v,trigger,List.of(),true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(v,trigger,List.of(transition("IN_PROGRESS",null)),true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(v,trigger,List.of(transition("SCHEDULED",6L)),true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(visit(Visit.Status.EXCEPTION,3L,null,2),trigger,history,true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(visit(Visit.Status.EXCEPTION,7L,null,1),trigger,history,true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(visit(Visit.Status.EXCEPTION,3L,8L,1),trigger,history,true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(visit(Visit.Status.SCHEDULED,3L,null,1),trigger,history,true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(v.arrivedAfterMissedCheckIn(now),trigger,history,true)).isFalse();
        assertThatThrownBy(()->visit(Visit.Status.SCHEDULED,3L,null,1).arrivedAfterMissedCheckIn(now)).hasMessageContaining("cannot");
    }
    @Test void onlyStorageQuantizationOfTheSameTriggerTimeIsAccepted() {
        var v=visit(Visit.Status.EXCEPTION,3L,null,1);
        var precise=new MissedCheckInTrigger(1L,11L,3L,trigger.scheduledStart(),trigger.dueAt(),0,now.plusNanos(987654000));
        assertThat(MissedCheckInResumePolicy.permits(v,precise,List.of(transition("SCHEDULED",null)),true)).isTrue();
        var rounded=new VisitStateTransition(2L,1L,"SCHEDULED","EXCEPTION",null,VisitStateTransition.Result.APPLIED,null,now.plusSeconds(1));
        assertThat(MissedCheckInResumePolicy.permits(v,precise,List.of(rounded),true)).isTrue();
        var unrelated=new VisitStateTransition(3L,1L,"SCHEDULED","EXCEPTION",null,VisitStateTransition.Result.APPLIED,null,now.plusSeconds(2));
        assertThat(MissedCheckInResumePolicy.permits(v,precise,List.of(unrelated),true)).isFalse();
        assertThat(MissedCheckInResumePolicy.permits(v,trigger,List.of(rounded),true)).isFalse();
    }
}
