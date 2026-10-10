package sg.nus.carelink.shared.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import sg.nus.carelink.shared.audit.persistence.entity.AuditLogJpaEntity;
import sg.nus.carelink.shared.audit.persistence.repository.AuditLogJpaRepository;

class WorkPackAccessAuditTest {
    private final AuditLogJpaRepository logs = mock(AuditLogJpaRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T01:00:00Z"), ZoneOffset.UTC);
    private final WorkPackAccessAudit audit = new WorkPackAccessAudit(logs, clock);

    @ParameterizedTest
    @ValueSource(strings = {"OK", "DENIED", "FAILED"})
    void appendsTheActorResourceOutcomeAndServerTimeWithoutClinicalDetails(String outcome) {
        audit.workPack(20L, 7L, outcome);

        var row = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(logs).saveAndFlush(row.capture());
        assertThat(row.getValue().getActorUserId()).isEqualTo(20L);
        assertThat(row.getValue().getResourceId()).isEqualTo(7L);
        assertThat(row.getValue().getResourceType()).isEqualTo("VISIT_WORK_PACK");
        assertThat(row.getValue().getAction()).isEqualTo("READ");
        assertThat(row.getValue().getResult().name()).isEqualTo(outcome);
        assertThat(row.getValue().getOccurredAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(row.getValue().getDetail()).isNull();
    }

    @Test
    void doesNotSwallowAnAuditStorageFailure() {
        var failure = new IllegalStateException("audit unavailable");
        when(logs.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> audit.workPack(20L, 7L, "OK")).isSameAs(failure);
    }
}
