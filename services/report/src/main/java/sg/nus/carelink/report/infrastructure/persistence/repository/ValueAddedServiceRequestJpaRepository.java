package sg.nus.carelink.report.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;

/** Spring Data repository for value_added_service_request. */
public interface ValueAddedServiceRequestJpaRepository extends JpaRepository<ValueAddedServiceRequestJpaEntity, Long> {
    List<ValueAddedServiceRequestJpaEntity> findByElderIdOrderByCreatedAtDesc(Long elderId);
    List<ValueAddedServiceRequestJpaEntity> findAllByOrderByCreatedAtDescIdDesc();
    List<ValueAddedServiceRequestJpaEntity> findByStatus(ValueAddedServiceRequestJpaEntity.Status status);
    boolean existsByValueAddedServiceId(Long valueAddedServiceId);

    /** One row per service asked for: the service's id and how many requests name it. */
    @Query("select r.valueAddedServiceId as serviceId, count(r) as requests from ValueAddedServiceRequestJpaEntity r "
            + "group by r.valueAddedServiceId")
    List<ServiceRequestCount> countGroupedByService();

    interface ServiceRequestCount {
        Long getServiceId();
        Long getRequests();
    }
}
