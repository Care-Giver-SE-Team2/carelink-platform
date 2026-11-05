package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A job report dispatched to an Open Platform provider was created or changed: dispatched, answered
 * by the provider through the job API, completed or called off. It carries the job's state after the
 * change, as the job API's {@code JobSummary} shows it, and nothing that names the elder. Published by
 * report in the transaction that saves the job; handled by notification, which sends the provider
 * {@code job.dispatched} for {@code NEW} and {@code job.cancelled} for {@code CANCELLED}.
 *
 * @param jobId report's id; the job API shows it as {@code JOB-<jobId>}
 * @param status {@code NEW}, {@code ACCEPTED}, {@code DECLINED}, {@code COMPLETED} or {@code CANCELLED}
 * @param district the Singapore postal district, two digits; empty when the elder's postal code is unknown
 * @param respondBy until when a {@code NEW} job can be answered; null once it is not {@code NEW}
 * @param receivedAt when report dispatched the job to the provider
 * @param cancelReason why a {@code CANCELLED} job was called off; null otherwise
 * @param version increases with every change to the job: a handler ignores a version older than the
 *        one it has
 */
public record ProviderJobUpdated(Long jobId, Long providerId, Long valueAddedServiceRequestId, String status,
		String serviceName, String district, String areaName, OffsetDateTime scheduledStart,
		Integer durationMinutes, OffsetDateTime respondBy, OffsetDateTime receivedAt, String cancelReason,
		Integer version, OffsetDateTime changedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "ProviderJobUpdated";

}
