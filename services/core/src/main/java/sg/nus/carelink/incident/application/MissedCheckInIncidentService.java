package sg.nus.carelink.incident.application;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class MissedCheckInIncidentService implements MissedCheckInIncidentGateway {
    private final IncidentService incidents;
    MissedCheckInIncidentService(IncidentService incidents) {
        this.incidents = incidents;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Long raise(Long elderId, Long visitId, LocalDateTime dueAt, LocalDateTime observedAt) {
        return incidents.raiseForMissedCheckIn(elderId, visitId, dueAt, observedAt).id();
    }
}
