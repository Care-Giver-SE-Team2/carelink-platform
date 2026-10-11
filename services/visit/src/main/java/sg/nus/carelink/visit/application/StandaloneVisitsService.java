package sg.nus.carelink.visit.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitInstructionRepository;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

@Service
@Transactional
class StandaloneVisitsService implements StandaloneVisits {

	/** visit.instructions holds this many characters. */
	static final int INSTRUCTIONS_MAX = 1000;

	private final VisitRepository visits;
	private final VisitInstructionRepository instructions;

	StandaloneVisitsService(VisitRepository visits, VisitInstructionRepository instructions) {
		this.visits = visits;
		this.instructions = instructions;
	}

	@Override
	public Long schedule(NewVisit visit) {
		Visit saved = visits.save(Visit.scheduled(visit.elderId(), visit.caregiverId(), null, null,
				visit.serviceType(), visit.start(), visit.end()));
		String text = visit.instructions() == null ? null : visit.instructions().strip();
		if (text != null && !text.isEmpty()) {
			instructions.save(saved.id(), text.length() > INSTRUCTIONS_MAX ? text.substring(0, INSTRUCTIONS_MAX) : text);
		}
		return saved.id();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<State> find(Long visitId) {
		return visits.findById(visitId).map(visit -> new State(visit.id(), visit.caregiverId(), visit.status().name(),
				visit.checkedInAt() != null));
	}
}
