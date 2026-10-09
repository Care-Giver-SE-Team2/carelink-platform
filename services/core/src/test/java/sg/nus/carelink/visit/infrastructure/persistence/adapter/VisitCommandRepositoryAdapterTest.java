package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitJpaRepository;

class VisitCommandRepositoryAdapterTest {
    private final VisitJpaRepository jpa=mock();
    private final JdbcTemplate jdbc=mock();
    private final EntityManager em=mock();
    private final VisitCommandRepositoryAdapter adapter=new VisitCommandRepositoryAdapter(jpa,jdbc,em);
    @Test void missingRowDoesNotLockAndCachedRowIsRefreshedWithWriteLock() {
        assertThat(adapter.lock(1L)).isEmpty();
        var row=new VisitJpaEntity();row.setId(1L);row.setVersion(0);
        when(em.find(VisitJpaEntity.class,1L)).thenReturn(row);
        doAnswer(_->{row.setVersion(4);return null;}).when(em).refresh(row,LockModeType.PESSIMISTIC_WRITE);
        assertThat(adapter.lock(1L)).get().extracting(sg.nus.carelink.visit.domain.model.Visit::version).isEqualTo(4);
        verify(jpa,never()).findForCommand(any());
    }
    @Test void failedVersionGuardNeverPretendsToSave() {
        var row=new VisitJpaEntity();row.setId(1L);row.setVersion(7);
        var visit=VisitMapper.toDomain(row);
        assertThatThrownBy(()->adapter.save(visit)).isInstanceOf(BusinessRuleViolation.class);
        verify(jpa,never()).findById(any());
    }
    @Test void commandDatesUseExistingJpaTimestampConvention() {
        var row=new VisitJpaEntity();row.setId(1L);row.setVersion(7);
        var at=java.time.LocalDateTime.of(2026,10,8,10,15);
        row.setCheckedInAt(at);row.setCheckedOutAt(at.plusHours(1));row.setStateDeadline(at.plusHours(2));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(jpa.findById(1L)).thenReturn(java.util.Optional.of(row));
        adapter.save(VisitMapper.toDomain(row));
        verify(jdbc).update(anyString(),eq("SCHEDULED"),eq(java.sql.Timestamp.valueOf(at)),
                eq(java.sql.Timestamp.valueOf(at.plusHours(1))),eq(java.sql.Timestamp.valueOf(at.plusHours(2))),isNull(),isNull(),eq(1L),eq(7));
        verify(em).refresh(row);
    }
}
