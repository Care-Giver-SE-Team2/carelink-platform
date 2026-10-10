package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;

class MissedCheckInPauseEvidenceTest {
    private final IncidentRepository incidents=mock();
    private final MissedCheckInPauseEvidence evidence=new MissedCheckInPauseEvidence(incidents);
    private Incident incident(long id,long visit,Incident.Source source,Long actor) {
        return new Incident(id,2L,visit,actor,null,source,Incident.Category.SERVICE,Incident.Severity.MEDIUM,
                Incident.Status.OPEN,null,null,null,"Synthetic evidence",null,null,null);
    }
    @Test void onlyTheLinkedSoleSystemIncidentProvesTheLegacyCause() {
        var original=incident(11,1,Incident.Source.SYSTEM_MISSED_CHECKIN,null);
        when(incidents.findByElder(2L)).thenReturn(List.of(original,incident(12,9,Incident.Source.CAREGIVER,3L)));
        assertThat(evidence.isSoleIncident(2L,1L,11L)).isTrue();
        assertThat(evidence.isSoleIncident(2L,1L,12L)).isFalse();
        when(incidents.findByElder(2L)).thenReturn(List.of(original,incident(13,1,Incident.Source.CAREGIVER,3L)));
        assertThat(evidence.isSoleIncident(2L,1L,11L)).isFalse();
        when(incidents.findByElder(2L)).thenReturn(List.of(incident(11,1,Incident.Source.CAREGIVER,3L)));
        assertThat(evidence.isSoleIncident(2L,1L,11L)).isFalse();
        when(incidents.findByElder(2L)).thenReturn(List.of());
        assertThat(evidence.isSoleIncident(2L,1L,11L)).isFalse();
    }
}
