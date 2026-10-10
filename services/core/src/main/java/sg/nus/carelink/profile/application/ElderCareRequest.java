package sg.nus.carelink.profile.application;

import java.time.LocalDateTime;
import java.util.List;

import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.NeedProgress;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Outcome;

/**
 * Care a family asked for on an elder's behalf, as the manager's care plan editor shows it: the
 * intake application that created the elder, or a later service application. careNeeds holds
 * care activity codes (careplan's catalog), plus free text on applications made before the forms
 * offered the catalog only. outcome and needs say how far the care plan has answered it; a
 * declined service application carries the reason the family was given.
 */
public record ElderCareRequest(Source source, Long applicationId, List<String> careNeeds, String notes,
		LocalDateTime submittedAt, Outcome outcome, List<NeedProgress> needs, String declineReason) {

	public enum Source { INTAKE, SERVICE_APPLICATION }
}
