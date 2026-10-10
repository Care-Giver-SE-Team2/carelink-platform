package sg.nus.carelink.report.controller.dto;

import sg.nus.carelink.rostering.application.VisitCover;

/**
 * One caregiver in the picker for a dispatched visit.
 *
 * @param rank 1 for the best suggestion; null when a hard rule excludes them
 * @param reason why they are suggested, or what excludes them
 */
public record CaregiverOptionResponse(Long caregiverId, String name, Integer rank, String reason, boolean eligible) {

    public static CaregiverOptionResponse from(VisitCover.Option option) {
        return new CaregiverOptionResponse(
                option.caregiverId(), option.name(), option.rank(), option.reason(), option.eligible());
    }
}
