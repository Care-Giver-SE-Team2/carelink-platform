package sg.nus.carelink.report.application;

import java.time.LocalDateTime;
import java.util.Optional;

/** The visits that carry out value-added services, which visit schedules outside any care plan. */
public interface StandaloneVisits {

	/** Schedules the visit and answers its id. */
	Long schedule(NewVisit visit);

	/** The visit's state now, or empty when there is no such visit. */
	Optional<State> find(Long visitId);

	record NewVisit(Long elderId, Long caregiverId, String serviceType, LocalDateTime start, LocalDateTime end,
			String instructions) {
	}

	record State(Long visitId, Long caregiverId, String status, boolean checkedIn) {
	}

}
