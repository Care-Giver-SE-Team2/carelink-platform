package sg.nus.carelink.rostering.application;

import java.time.LocalDate;

/**
 * Whether a caregiver is away on a given day. Another service asks it at the moment it hands
 * a caregiver work, such as an approved extra service, instead of reading the absence table.
 */
public interface LeaveCalendar {

	/** Whether the caregiver has approved leave covering the day. */
	boolean onApprovedLeave(Long caregiverId, LocalDate day);

}
