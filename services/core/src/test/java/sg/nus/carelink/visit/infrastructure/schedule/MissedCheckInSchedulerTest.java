package sg.nus.carelink.visit.infrastructure.schedule;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.visit.application.MissedCheckInScanService;

class MissedCheckInSchedulerTest {
    @Test void schedulerDelegatesAndSurvivesCandidateReadFailure() {
        MissedCheckInScanService scan=mock();var scheduler=new MissedCheckInScheduler(scan);
        when(scan.scan()).thenReturn(new MissedCheckInScanService.Outcome(0,0,0),new MissedCheckInScanService.Outcome(2,1,1))
                .thenThrow(new IllegalStateException("Synthetic read failure"));
        scheduler.scan();scheduler.scan();scheduler.scan();verify(scan,times(3)).scan();
    }
}
