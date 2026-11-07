package sg.nus.carelink.report.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 * Offering a value-added request to the Open Platform provider a manager picks, and calling the offer
 * off (docs/section5/decisions.md, D16 and D20). A manager decides who carries out each request: a
 * caregiver, or a provider that offers the service, only far enough ahead that the manager still has
 * time if the provider says no. One offer is open at a time; a provider that declines, or does not
 * answer in time, hands the request back, and the manager may offer it to another, never the same one
 * twice. While an offer is open the manager may still staff the visit, which withdraws it
 * ({@link #withdrawForStaffing}); a provider accepting calls the visit off ({@link ProviderJobService#accept}).
 * Both lock the job, so the second sees the first. Each change tells notification
 * ({@code ProviderJobUpdated}), which sends the provider its webhook.
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
     * The providers a manager may offer the request to: active, offering its service, and not offered
     * it before. Empty when the start is too near for any provider to answer in time.
     */
    @Transactional(readOnly = true)
    public List<Provider> optionsFor(ValueAddedServiceRequest request, ValueAddedService service, LocalDateTime now) {
        if (request.requestedSchedule() == null || !ProviderJob.offerable(request.requestedSchedule(), now)) {
            return List.of();
        }
        Set<Long> offered = jobs.providersOfferedFor(request.id());
        return providers.activeFor(service.name()).stream().filter(p -> !offered.contains(p.id())).toList();
    }

    /**
     * Offers the request to the provider the manager picked, which sends it {@code job.dispatched}.
     *
     * @throws BusinessRuleViolation {@code VALUE_ADDED_ALREADY_OFFERED} while an offer is open or a
     *         provider has taken it; {@code PROVIDER_NOT_AVAILABLE} for a provider not among
     *         {@link #optionsFor}; {@code VALUE_ADDED_TOO_LATE_FOR_PROVIDER} too near the start
     */
    public ProviderJob offerTo(ValueAddedServiceRequest request, ValueAddedService service, Long providerId,
            LocalDateTime now) {
        jobs.findByRequestIdForUpdate(request.id())
                .filter(job -> job.open() || job.status() == ProviderJob.Status.COMPLETED)
                .ifPresent(job -> {
                    throw new BusinessRuleViolation("VALUE_ADDED_ALREADY_OFFERED",
                            "This request is already with a provider. Wait for its answer, or assign a caregiver.");
                });
        if (request.requestedSchedule() == null || !ProviderJob.offerable(request.requestedSchedule(), now)) {
            throw new BusinessRuleViolation("VALUE_ADDED_TOO_LATE_FOR_PROVIDER",
                    "It is too close to the start for a provider to answer in time. Assign a caregiver.");
        }
        Provider provider = optionsFor(request, service, now).stream()
                .filter(p -> p.id().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolation("PROVIDER_NOT_AVAILABLE",
                        "This provider can't be offered the request: it is not active, does not offer the service, "
                                + "or was offered it before."));
        JobElders.Brief brief = elders.brief(request.elderId());
        ProviderJob job = jobs.create(ProviderJob.dispatched(provider.id(), request.id(), service.name(),
                brief.district(), brief.areaName(), brief.summary(), brief.languages(),
                request.requestedByFamilyMemberId() == null ? BOOKED_BY_ELDER : BOOKED_BY_FAMILY,
                request.requestedSchedule(), service.durationMinutes(), now));
        events.changed(job);
        return job;
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
