package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;

/**
 * A visit's special instructions for the caregiver (visit.instructions). Kept apart from the
 * Visit record, which every module's roster code builds, because only a standalone visit has
 * them today.
 */
public interface VisitInstructionRepository {
    Optional<String> find(Long visitId);
    void save(Long visitId, String instructions);
}
