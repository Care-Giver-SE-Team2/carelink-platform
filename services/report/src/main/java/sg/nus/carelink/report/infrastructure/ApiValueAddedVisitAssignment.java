package sg.nus.carelink.report.infrastructure;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.ValueAddedVisitAssignment;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * The elder's primary caregiver, as core has them, unless they are away that day (core) or already
 * have a visit booked or under way in that time (visit). Asked at the moment of choosing rather than
 * kept as a copy that could lag (docs/platform/event-catalogue.md, section 4).
 */
@Component
class ApiValueAddedVisitAssignment implements ValueAddedVisitAssignment {

	private final CoreApi core;
	private final VisitApi visit;

	ApiValueAddedVisitAssignment(CoreApi core, VisitApi visit) {
		this.core = core;
		this.visit = visit;
	}

	@Override
	public Optional<Long> chooseCaregiver(Long elderId, LocalDateTime start, LocalDateTime end) {
		Optional<Long> candidate = core.findPrimaryCaregiverId(elderId);
		if (candidate.isEmpty()) {
			return Optional.empty();
		}
		Long caregiverId = candidate.get();
		if (core.onLeave(caregiverId, start.toLocalDate()).onLeave()) {
			return Optional.empty();
		}
		return visit.caregiverBusy(caregiverId, start, end).busy() ? Optional.empty() : candidate;
	}

}
