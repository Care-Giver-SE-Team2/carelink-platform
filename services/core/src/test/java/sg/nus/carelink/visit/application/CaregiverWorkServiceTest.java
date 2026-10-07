package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.careplan.application.VisitPlanReader;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.shared.audit.AccessAudit;
import sg.nus.carelink.shared.error.*;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.*;

class CaregiverWorkServiceTest {
    final VisitRepository visits = mock(VisitRepository.class);
    final VisitTaskRepository tasks = mock(VisitTaskRepository.class);
    final CaregiverWorkDirectory directory = mock(CaregiverWorkDirectory.class);
    final VisitPlanReader plans = mock(VisitPlanReader.class);
    final AccessAudit audit = mock(AccessAudit.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:00:00Z"), ZoneOffset.UTC);
    final CaregiverWorkService service = new CaregiverWorkService(visits,tasks,directory,plans,audit,clock);
    final LocalDate day = LocalDate.of(2026,9,24);

    @BeforeEach void profile() {
        when(directory.require("a")).thenReturn(new CaregiverWorkDirectory.Profile(2L,20L,"A",null,null,null,"AVAILABLE"));
        when(directory.alerts(2L,day)).thenReturn(new CaregiverWorkDirectory.CredentialAlerts(
            List.of(), new CaregiverWorkDirectory.CredentialAlertContext(day,30,false)));
    }
    Visit visit(Long caregiver) {
        return new Visit(1L,3L,caregiver,11L,null,"CARE",day.atTime(9,0),day.atTime(10,0),
            null,null,Visit.Status.SCHEDULED,null,4L,0,null,null);
    }
    @Test void defaultScheduleUsesSingaporeTodayAndOnlyCurrentCaregiver() {
        when(visits.findAssigned(2L,day.atStartOfDay(),day.plusDays(7).atStartOfDay())).thenReturn(List.of());
        var result = service.schedule("a",null,null);
        assertThat(result.dateFrom()).isEqualTo(day);
        assertThat(result.dateTo()).isEqualTo(day.plusDays(6));
        assertThat(result.timeZone()).isEqualTo("Asia/Singapore");
        assertThat(result.credentialAlertContext().asOfDate()).isEqualTo(day);
        verify(visits).findAssigned(2L,day.atStartOfDay(),day.plusDays(7).atStartOfDay());
        verify(directory).alerts(2L,day);
    }
    @Test void rejectsPartialReversedAndTooLongRanges() {
        assertThatThrownBy(() -> service.schedule("a",day,null)).isInstanceOf(CaregiverWorkService.InvalidDateRange.class);
        assertThatThrownBy(() -> service.schedule("a",day,day.minusDays(1))).isInstanceOf(CaregiverWorkService.InvalidDateRange.class);
        assertThatThrownBy(() -> service.schedule("a",day,day.plusDays(31))).isInstanceOf(CaregiverWorkService.InvalidDateRange.class);
        verifyNoInteractions(visits);
    }
    @Test void accepts31InclusiveDays() {
        service.schedule("a",day,day.plusDays(30));
        verify(visits).findAssigned(2L,day.atStartOfDay(),day.plusDays(31).atStartOfDay());
    }

    @Test void selectingAPastRosterDoesNotBackdateCredentialAssessment() {
        var past = day.minusMonths(2);
        var result = service.schedule("a",past,past);
        verify(directory).alerts(2L,day);
        assertThat(result.credentialAlertContext().asOfDate()).isEqualTo(day);
        assertThat(result.dateFrom()).isEqualTo(past);
    }
    @Test void otherCaregiverIsDeniedBeforeAnyClinicalDataIsRead() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(9L)));
        assertThatThrownBy(() -> service.workPack("a",1L)).isInstanceOf(AccessDeniedException.class);
        verify(audit).workPack(20L,1L,"DENIED");
        verifyNoInteractions(plans,tasks);
        verify(directory,never()).elder(any());
    }
    @Test void unassignedVisitIsAlsoDenied() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(null)));
        assertThatThrownBy(() -> service.workPack("a",1L)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void cancellationIsDeniedAndAuditedBeforeProtectedDataIsRead() {
        var cancelled = mock(Visit.class);
        when(cancelled.caregiverId()).thenReturn(2L);
        when(cancelled.status()).thenReturn(Visit.Status.CANCELLED);
        when(visits.findById(1L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.workPack("a",1L)).isInstanceOfSatisfying(BusinessRuleViolation.class,
                ex -> assertThat(ex.code()).isEqualTo("VISIT_CANCELLED"));
        verify(audit).workPack(20L,1L,"DENIED");
        verifyNoMoreInteractions(audit);
        verifyNoInteractions(plans,tasks);
        verify(directory,never()).elder(any());
    }
    @Test void foreignCancelledVisitDoesNotDiscloseCancellation() {
        var cancelled = mock(Visit.class);
        when(cancelled.caregiverId()).thenReturn(9L);
        when(visits.findById(1L)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> service.workPack("a",1L)).isInstanceOf(AccessDeniedException.class);
        verify(cancelled,never()).status();
        verifyNoInteractions(plans,tasks);
    }
    @Test void missingVisitIs404AndAudited() {
        assertThatThrownBy(() -> service.workPack("a",999L)).isInstanceOf(ResourceNotFound.class);
        verify(audit).workPack(20L,999L,"FAILED");
    }
    @Test void usesBoundVersionAndOnlyAssignedTaskEvidence() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(2L)));
        when(directory.elder(3L)).thenReturn(new CaregiverWorkDirectory.ElderView(3L,"Mei","Address","North",List.of("English"),null,null));
        when(plans.read(4L,3L)).thenReturn(new VisitPlanReader.Snapshot(4L,1,List.of(
            new VisitPlanReader.Task(11L,"Hygiene","CHECKLIST"),
            new VisitPlanReader.Task(12L,"Chat","NONE"),
            new VisitPlanReader.Task(13L,"Unassigned","PHOTO"))));
        when(tasks.findByVisitId(1L)).thenReturn(List.of(
            new VisitTask(5L,1L,11L,"Hygiene",VisitTask.Status.PENDING,null,null,null),
            new VisitTask(6L,1L,12L,"Chat",VisitTask.Status.PENDING,null,null,null)));
        var result = service.workPack("a",1L);
        assertThat(result.carePlanVersion()).isEqualTo(1);
        assertThat(result.requiredEvidenceKinds()).containsExactly("CHECKLIST");
        assertThat(result.serviceInstructions()).containsExactly("Hygiene","Chat");
        verify(audit).workPack(20L,1L,"OK");
    }
    @Test void inconsistentPlanTaskFailsClosedAndIsAudited() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(2L)));
        when(plans.read(4L,3L)).thenReturn(new VisitPlanReader.Snapshot(4L,1,List.of()));
        assertThatThrownBy(() -> service.workPack("a",1L)).isInstanceOf(BusinessRuleViolation.class);
        verify(audit).workPack(20L,1L,"FAILED");
        verify(audit,never()).workPack(20L,1L,"OK");
    }
}
