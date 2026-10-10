package sg.nus.carelink.incident.application;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;

/** Read-only, conservative evidence for the visit-owned legacy SYS03 compatibility command. */
@Service
@Transactional(readOnly = true)
public class MissedCheckInPauseEvidence {
    private final IncidentRepository incidents;
    public MissedCheckInPauseEvidence(IncidentRepository incidents) { this.incidents = incidents; }
    public boolean isSoleIncident(Long elderId, Long visitId, Long incidentId) {
        var related = incidents.findByElder(elderId).stream()
                .filter(i -> Objects.equals(i.visitId(), visitId)).toList();
        // A second report may not have advanced an already-EXCEPTION Visit's version.
        // Even resolved additional incidents are not guessed to be harmless.
        return related.size() == 1 && Objects.equals(related.getFirst().id(), incidentId)
                && Objects.equals(related.getFirst().elderId(), elderId)
                && related.getFirst().source() == Incident.Source.SYSTEM_MISSED_CHECKIN
                && related.getFirst().reportedByUserId() == null;
    }
}
