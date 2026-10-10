package sg.nus.carelink.visit.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.repository.ElderConfirmationRepository;

/**
 * In-memory test double for elder_confirmation.
 */
class InMemoryElderConfirmationRepository
        implements ElderConfirmationRepository {

    private final Map<Long, ElderConfirmation> rows =
            new HashMap<>();

    private long nextId = 1;

    @Override
    public Optional<ElderConfirmation> findById(
            Long id) {

        return Optional.ofNullable(
                rows.get(id)
        );
    }

    @Override
    public Optional<ElderConfirmation> findByVisitId(
            Long visitId) {

        return rows.values()
                .stream()
                .filter(confirmation ->
                        visitId.equals(
                                confirmation.visitId()
                        )
                )
                .findFirst();
    }

    @Override
    public boolean existsByVisitId(
            Long visitId) {

        return findByVisitId(
                visitId
        ).isPresent();
    }

    @Override
    public ElderConfirmation save(
            ElderConfirmation confirmation) {

        ElderConfirmation stored =
                confirmation.id() == null
                        ? new ElderConfirmation(
                                nextId++,
                                confirmation.visitId(),
                                confirmation.elderId(),
                                confirmation.confirmationStatus(),
                                confirmation.rating(),
                                confirmation.comment(),
                                confirmation.confirmedAt()
                        )
                        : confirmation;

        rows.put(
                stored.id(),
                stored
        );

        return stored;
    }
}