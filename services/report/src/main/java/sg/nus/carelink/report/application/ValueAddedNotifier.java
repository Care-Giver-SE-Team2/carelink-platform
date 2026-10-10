package sg.nus.carelink.report.application;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;

/**
 * Application port: who hears about an extra-service request as it moves. The elder has no
 * inbox, and reads every change on their own request list; the family hears through their bell,
 * the caregiver through theirs. Managers are told separately, by {@link ValueAddedManagerAlert}.
 */
public interface ValueAddedNotifier {

    /** The elder asked; the family's answer is needed. */
    void requested(ValueAddedServiceRequest request, String serviceName);

    /** Still unanswered as the requested time nears; sent once per request however often it is asked. */
    void reminder(ValueAddedServiceRequest request, String serviceName);

    /** A caregiver was put on the request's visit, at approval or later by a manager. */
    void caregiverAssigned(ValueAddedServiceRequest request, String serviceName, Long caregiverId);

    /** The request was called off; {@code caregiverId} is whoever was on its visit, if anybody. */
    void cancelled(ValueAddedServiceRequest request, String serviceName, Long caregiverId, Why why);

    enum Why {
        /** A manager cancelled it. */
        BY_MANAGER,
        /** The elder withdrew it. */
        BY_ELDER,
        /** Its visit failed before anybody started on it. */
        NOT_PROVIDED,
        /** The family did not answer before the requested time. */
        NOT_ANSWERED
    }
}
