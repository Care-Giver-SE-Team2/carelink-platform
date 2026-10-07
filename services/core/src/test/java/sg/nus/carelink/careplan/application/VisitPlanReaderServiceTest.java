package sg.nus.carelink.careplan.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.repository.*;
import sg.nus.carelink.shared.error.*;

class VisitPlanReaderServiceTest {
    final CarePlanRepository plans = mock(CarePlanRepository.class);
    final CarePlanNodeRepository nodes = mock(CarePlanNodeRepository.class);
    final VisitPlanReaderService service = new VisitPlanReaderService(plans,nodes);
    CarePlan plan(Long elder, LocalDateTime published) {
        var plan = mock(CarePlan.class);
        when(plan.id()).thenReturn(4L);
        when(plan.elderId()).thenReturn(elder);
        when(plan.version()).thenReturn(1);
        when(plan.publishedAt()).thenReturn(published);
        when(plans.findById(4L)).thenReturn(Optional.of(plan));
        return plan;
    }
    @Test void readsExactPublishedVersionRegardlessOfLaterVersions() {
        plan(3L,LocalDateTime.of(2026,9,1,0,0));
        assertThat(service.read(4L,3L).version()).isEqualTo(1);
        verify(plans).findById(4L);
        verifyNoMoreInteractions(plans);
    }
    @Test void wrongElderAndUnpublishedFailClosed() {
        plan(9L,LocalDateTime.now());
        assertThatThrownBy(() -> service.read(4L,3L)).isInstanceOf(BusinessRuleViolation.class);
        plan(3L,null);
        assertThatThrownBy(() -> service.read(4L,3L)).isInstanceOf(BusinessRuleViolation.class);
        verifyNoInteractions(nodes);
    }
    @Test void missingVersionIsNotReplacedWithLatest() {
        assertThatThrownBy(() -> service.read(4L,3L)).isInstanceOf(ResourceNotFound.class);
        verifyNoInteractions(nodes);
    }
}
