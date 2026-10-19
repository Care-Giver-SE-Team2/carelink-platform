package sg.nus.carelink.report.application;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.domain.model.Provider;
import sg.nus.carelink.report.domain.model.ProviderJob;
import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ProviderJobs;
import sg.nus.carelink.report.domain.repository.Providers;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Offering a value-added request to an Open Platform provider, and calling the offer off
 * (docs/section5/decisions.md, D16 and D17). Caregivers come first: a request is offered only when the
 * primary caregiver is not free, alongside the visit a manager may staff, and only far enough ahead
 * that the manager still has time if the provider says no. Whoever acts first has it: a manager
 * staffing the visit withdraws the offer ({@link #withdrawForStaffing}), and a provider accepting calls
 * the visit off ({@link ProviderJobService#accept}). Both lock the job, so the second sees the first.
 * Each change tells notification ({@code ProviderJobUpdated}), which sends the provider its webhook.
 */
@Service
@Transactional
public class ProviderJobDispatch {

    static final String STAFFED_REASON = "CareLink has staffed this job itself.";

    static final String BOOKED_BY_ELDER = "The elder";
    static final String BOOKED_BY_FAMILY = "Family member";

    private final Providers providers;
    private final ProviderJobs jobs;
    private final JobElders elders;
    private final ProviderJobEvents events;

    public ProviderJobDispatch(Providers providers, ProviderJobs jobs, JobElders elders, ProviderJobEvents events) {
        this.providers = providers;
        this.jobs = jobs;
        this.elders = elders;
        this.events = events;
    }

    /**
     * Offers the request to the provider that offers its service and was offered a job least
     * recently. Nothing when the start is too near, or no active provider offers the service.
     */
    public Optional<ProviderJob> offer(ValueAddedServiceRequest request, ValueAddedService service,
            LocalDateTime now) {
        if (request.requestedSchedule() == null || !ProviderJob.offerable(request.requestedSchedule(), now)) {
            return Optional.empty();
        }
        Optional<Provider> provider = providers.nextFor(service.name());
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        JobElders.Brief brief = elders.brief(request.elderId());
        ProviderJob job = jobs.create(ProviderJob.dispatched(provider.get().id(), request.id(), service.name(),
                brief.district(), brief.areaName(), brief.summary(), brief.languages(),
                request.requestedByFamilyMemberId() == null ? BOOKED_BY_ELDER : BOOKED_BY_FAMILY,
                request.requestedSchedule(), service.durationMinutes(), now));
        events.changed(job);
        return Optional.of(job);
    }

    /** Calls off the request's job, if it has one the provider may still act on, giving it the reason. */
    public Optional<ProviderJob> cancelFor(Long requestId, String reason, LocalDateTime now) {
        return jobs.findByRequestIdForUpdate(requestId)
                .filter(ProviderJob::open)
                .map(job -> {
                    ProviderJob cancelled = job.cancelled(reason, now);
                    jobs.update(cancelled);
                    events.changed(cancelled);
                    return cancelled;
                });
    }

    /**
     * A manager is putting a caregiver on the request's visit: a NEW offer is withdrawn, with the
     * provider told. Refused when the provider has already taken the job.
     */
    public void withdrawForStaffing(Long requestId, LocalDateTime now) {
        jobs.findByRequestIdForUpdate(requestId).ifPresent(job -> {
            if (job.status() == ProviderJob.Status.ACCEPTED || job.status() == ProviderJob.Status.COMPLETED) {
                throw new BusinessRuleViolation("VALUE_ADDED_TAKEN_BY_PROVIDER",
                        "A provider has already taken this service; cancel the request to call it off.");
            }
            if (job.status() == ProviderJob.Status.NEW) {
                ProviderJob withdrawn = job.cancelled(STAFFED_REASON, now);
                jobs.update(withdrawn);
                events.changed(withdrawn);
            }
        });
    }

    /** Whether a provider has taken the request on: its visit was called off for that, not for nothing. */
    @Transactional(readOnly = true)
    public boolean heldByProvider(Long requestId) {
        return jobs.findByRequestId(requestId)
                .filter(job -> job.status() == ProviderJob.Status.ACCEPTED
                        || job.status() == ProviderJob.Status.COMPLETED)
                .isPresent();
    }

    /** The request's job and its provider's name, for the manager. */
    @Transactional(readOnly = true)
    public Optional<Offer> findFor(Long requestId) {
        return jobs.findByRequestId(requestId).map(job -> new Offer(job,
                providers.find(job.providerId()).map(Provider::organisationName).orElse(null)));
    }

    public record Offer(ProviderJob job, String providerName) {
    }
}
