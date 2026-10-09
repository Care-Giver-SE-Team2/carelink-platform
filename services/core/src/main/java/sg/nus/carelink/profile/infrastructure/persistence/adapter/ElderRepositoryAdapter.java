package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.repository.ElderRepository;
import sg.nus.carelink.profile.infrastructure.persistence.repository.ElderJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points
 * infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class ElderRepositoryAdapter implements ElderRepository {

    private final ElderJpaRepository jpa;

    ElderRepositoryAdapter(ElderJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Elder> findById(Long id) {
        return jpa.findById(id).map(ElderMapper::toDomain);
    }

    @Override
    public Optional<Elder> findByUserId(Long userId) {
        return jpa.findByUserId(userId).map(ElderMapper::toDomain);
    }

    @Override
    public List<Elder> findAll() {
        return jpa.findAll().stream().map(ElderMapper::toDomain).toList();
    }

    @Override
    public List<Elder> findByPostalCode(String postalCode) {
        return jpa.findByPostalCode(postalCode).stream().map(ElderMapper::toDomain).toList();
    }

    @Override
    public List<Elder> findByIds(Set<Long> elderIds) {
        return jpa.findByIdInOrderByIdAsc(elderIds).stream().map(ElderMapper::toDomain).toList();
    }

    @Override
    public Elder save(Elder elder) {
        return ElderMapper.toDomain(jpa.save(ElderMapper.toEntity(elder)));
    }
}
