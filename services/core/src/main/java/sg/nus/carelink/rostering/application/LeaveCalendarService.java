package sg.nus.carelink.rostering.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;

@Service
@Transactional(readOnly = true)
class LeaveCalendarService implements LeaveCalendar {

	private final AbsenceReportRepository absences;

	LeaveCalendarService(AbsenceReportRepository absences) {
		this.absences = absences;
	}

	@Override
	public boolean onApprovedLeave(Long caregiverId, LocalDate day) {
		return absences.findApprovedOverlapping(day, day).stream()
				.anyMatch(absence -> caregiverId.equals(absence.caregiverId()));
	}

}
