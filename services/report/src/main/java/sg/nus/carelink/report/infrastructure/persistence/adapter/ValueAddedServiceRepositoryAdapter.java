package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRepository;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceJpaRepository;

@Repository
class ValueAddedServiceRepositoryAdapter implements ValueAddedServiceRepository {
    private final ValueAddedServiceJpaRepository jpa;

    ValueAddedServiceRepositoryAdapter(ValueAddedServiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ValueAddedService> findById(Long id) {
        return jpa.findById(id).map(ValueAddedServiceMapper::toDomain);
    }

    @Override
    public List<ValueAddedService> findAvailable() {
        return jpa.findByStatusOrderByNameAsc(ValueAddedServiceJpaEntity.Status.AVAILABLE)
                .stream().map(ValueAddedServiceMapper::toDomain).toList();
    }

    @Override
    public List<ValueAddedService> findAll() {
        return jpa.findAll().stream().map(ValueAddedServiceMapper::toDomain).toList();
    }

    @Override
    public ValueAddedService save(ValueAddedService service) {
        return ValueAddedServiceMapper.toDomain(jpa.save(ValueAddedServiceMapper.toEntity(service)));
    }
}
