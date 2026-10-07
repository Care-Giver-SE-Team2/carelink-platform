package sg.nus.carelink.profile.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.repository.ElderRepository;

/**
 * Test double for the port: the service is exercised without Spring
 * or a database (as in identity).
 */
class InMemoryElderRepository implements ElderRepository {

    private final Map<Long, Elder> rows = new HashMap<>();
    private long nextId = 1;

    @Override
    public Optional<Elder> findById(Long id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<Elder> findAll() {
        return List.copyOf(rows.values());
    }

    @Override
    public List<Elder> findByIds(Set<Long> elderIds) {
        return rows.values().stream()
                .filter(elder -> elderIds.contains(elder.id()))
                .sorted(Comparator.comparing(Elder::id))
                .toList();
    }

    @Override
    public Optional<Elder> findByUserId(Long userId) {
        return rows.values()
                .stream()
                .filter(elder -> userId.equals(elder.userId()))
                .findFirst();
    }

    @Override
    public List<Elder> findByPostalCode(String postalCode) {
        return rows.values().stream().filter(elder -> postalCode.equals(elder.postalCode())).toList();
    }

    @Override
    public Elder save(Elder elder) {

        Elder stored = elder.id() == null
                ? new Elder(
                        nextId,
                        elder.userId(),
                        elder.fullName(),
                        elder.gender(),
                        elder.dateOfBirth(),
                        elder.phone(),
                        elder.address(),
                        elder.postalCode(),
                        elder.sector(),
                        elder.preferredDialects(),
                        elder.livesAlone(),
                        elder.mobilityLevel(),
                        elder.continuityPreference(),
                        elder.medicalNotes(),
                        elder.createdAt(),
                        elder.updatedAt())
                : elder;

        rows.put(stored.id(), stored);

        if (elder.id() == null) {
            nextId++;
        }

        return stored;
    }
}
