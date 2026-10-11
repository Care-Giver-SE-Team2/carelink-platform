package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;
import java.util.UUID;
import sg.nus.carelink.visit.domain.model.CaregiverCommandReceipt;

public interface CaregiverCommandStore {
    Optional<CaregiverCommandReceipt> find(Long actor, UUID key);
    Optional<UUID> incidentKey(Long actor, Long incidentId);
    void save(CaregiverCommandReceipt receipt);
    void audit(Long actor, Long visitId, String action, String result, String reason);
}
