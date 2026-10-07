package sg.nus.carelink.shared.audit.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry.Outcome;
import sg.nus.carelink.shared.audit.persistence.entity.AuditLogJpaEntity;
import sg.nus.carelink.shared.audit.persistence.repository.AuditLogJpaRepository;

/**
 * Checks append-only audit mapping and server timestamps without a database.
 *
 * @author Wang Zhili
 */
class JpaAccessAuditTest {

	private final AuditLogJpaRepository rows = mock(AuditLogJpaRepository.class);
	private final JpaAccessAudit audit = new JpaAccessAudit(rows,
			Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"), ZoneOffset.UTC));

	@ParameterizedTest
	@EnumSource(Outcome.class)
	void appendsTheActorResourceAndOutcomeWithTheServersSingaporeTime(Outcome outcome) {
		var entry = new AccessAuditEntry(1101L, "READ", "CAREGIVER", 201L,
				outcome, "operation=FM02_READ_CAREGIVER");

		audit.append(entry);

		var captured = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
		verify(rows).saveAndFlush(captured.capture());
		AuditLogJpaEntity row = captured.getValue();
		assertThat(row.getId()).isNull();
		assertThat(row.getActorUserId()).isEqualTo(1101L);
		assertThat(row.getAction()).isEqualTo("READ");
		assertThat(row.getResourceType()).isEqualTo("CAREGIVER");
		assertThat(row.getResourceId()).isEqualTo(201L);
		assertThat(row.getResult()).isEqualTo(AuditLogJpaEntity.Result.valueOf(outcome.name()));
		assertThat(row.getDetail()).isEqualTo("operation=FM02_READ_CAREGIVER");
		assertThat(row.getOccurredAt()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 30));
		verifyNoMoreInteractions(rows);
	}

	@Test
	void preservesAnUnknownActorAndACollectionWithoutAnIndividualResourceId() {
		audit.append(new AccessAuditEntry(null, "READ", "ELDER", null, Outcome.DENIED, null));

		var captured = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
		verify(rows).saveAndFlush(captured.capture());
		assertThat(captured.getValue().getActorUserId()).isNull();
		assertThat(captured.getValue().getResourceId()).isNull();
		assertThat(captured.getValue().getDetail()).isNull();
	}

	@Test
	void repeatedReadsCreateSeparateRowsInsteadOfUpdatingAnEarlierAuditEntry() {
		var entry = new AccessAuditEntry(1101L, "READ", "CAREGIVER_CREDENTIALS", 201L,
				Outcome.OK, "operation=FM02_LIST_CREDENTIALS");

		audit.append(entry);
		audit.append(entry);

		var captured = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
		verify(rows, times(2)).saveAndFlush(captured.capture());
		assertThat(captured.getAllValues().get(0)).isNotSameAs(captured.getAllValues().get(1));
		assertThat(captured.getAllValues()).hasSize(2).allSatisfy(row -> assertThat(row.getId()).isNull());
		verifyNoMoreInteractions(rows);
	}

	@Test
	void exposesAnInsertFailureWithoutRetryingOrClaimingSuccess() {
		var failure = new DataAccessResourceFailureException("Audit storage unavailable");
		doThrow(failure).when(rows).saveAndFlush(any(AuditLogJpaEntity.class));
		var entry = new AccessAuditEntry(1101L, "READ", "CAREGIVER", 201L, Outcome.OK, null);

		assertThatThrownBy(() -> audit.append(entry)).isSameAs(failure);

		verify(rows).saveAndFlush(any(AuditLogJpaEntity.class));
		verifyNoMoreInteractions(rows);
	}
}
