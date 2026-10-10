package sg.nus.carelink.rostering.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.rostering.application.LeaveCalendar;

/** rostering's part of core's internal API ({@link CoreApi}) on leave: whether a caregiver is away that day. */
@RestController
@RequestMapping("/internal/v1")
public class InternalLeaveController {

	private final LeaveCalendar leave;

	InternalLeaveController(LeaveCalendar leave) {
		this.leave = leave;
	}

	@GetMapping("/caregivers/{caregiverId}/on-leave")
	public CoreApi.OnLeave onLeave(@PathVariable Long caregiverId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
		return new CoreApi.OnLeave(leave.onApprovedLeave(caregiverId, day));
	}

}
