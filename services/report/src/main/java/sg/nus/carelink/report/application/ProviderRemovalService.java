package sg.nus.carelink.report.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.domain.model.Provider;
import sg.nus.carelink.report.domain.model.ProviderJob;
import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.domain.repository.ProviderJobs;
import sg.nus.carelink.report.domain.repository.Providers;
import sg.nus.carelink.report.domain.repository.ValueAddedServiceRequestRepository;

/**
 * Takes back the jobs of a provider a manager removed (docs/section5/decisions.md, D19), when report
 * hears of it. Every job it may still act on is called off, with the reason, and each request goes back
 * to the managers:
 *
 * <ul>
 * <li>a NEW job: the managers already have its visit, nobody on it, as when a provider declines;</li>
 * <li>an ACCEPTED job still to start: its visit was called off for the provider, so the request is given a
 * new one, nobody on it, and the managers are told to staff it;</li>
 * <li>an ACCEPTED job whose start has passed: too late to staff, so the request is closed and the family
 * told, as for a visit nobody started.</li>
 * </ul>
 *
 * <p>Jobs it completed, declined or had called off stay as they were. Running it again finds nothing
 * open, so a redelivered event changes nothing.
 */
@Service
@Transactional
public class ProviderRemovalService {

    static final String REMOVED_REASON = "CareLink has removed this provider; CareLink has made other arrangements.";

    private final Providers providers;
    private final ProviderJobs jobs;
    private final ProviderJobEvents events;
    private final ValueAddedServiceRequestRepository requests;
    private final StandaloneVisits visits;
    private final ValueAddedManagerAlert managerAlert;
    private final ValueAddedNotifier notifier;
    private final Clock clock;

    @SuppressWarnings("java:S107") // one collaborator per thing a job reaches
    public ProviderRemovalService(Providers providers, ProviderJobs jobs, ProviderJobEvents events,
            ValueAddedServiceRequestRepository requests, StandaloneVisits visits, ValueAddedManagerAlert managerAlert,
            ValueAddedNotifier notifier, Clock clock) {
        this.providers = providers;
        this.jobs = jobs;
        this.events = events;
        this.requests = requests;
        this.visits = visits;
        this.managerAlert = managerAlert;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** @return how many jobs were taken back */
    public int takeBackJobsOf(Long providerId) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        String providerName = providers.find(providerId).map(Provider::organisationName).orElse(null);
        int taken = 0;
        for (ProviderJob job : jobs.findOpenByProviderForUpdate(providerId)) {
            ProviderJob cancelled = job.cancelled(REMOVED_REASON, now);
            jobs.update(cancelled);
            events.changed(cancelled);
            if (job.status() == ProviderJob.Status.ACCEPTED) {
                requests.findById(job.requestId())
                        .filter(request -> request.status() == ValueAddedServiceRequest.Status.DISPATCHED)
                        .ifPresent(request -> handBack(request, job, providerName, now));
            }
            taken++;
        }
        return taken;
    }

    private void handBack(ValueAddedServiceRequest request, ProviderJob job, String providerName, LocalDateTime now) {
        LocalDateTime start = job.scheduledStart();
        if (!start.isAfter(now)) {
            ValueAddedServiceRequest closed = requests.save(request.cancelled());
            notifier.cancelled(closed, job.serviceName(), null, ValueAddedNotifier.Why.NOT_PROVIDED);
            return;
        }
        Long visitId = visits.schedule(new StandaloneVisits.NewVisit(request.elderId(), null, job.serviceName(), start,
                start.plusMinutes(job.durationMinutes()), request.specialInstructions()));
        requests.save(request.redispatchedTo(visitId));
        managerAlert.providerRemoved(visitId, request.elderId(), job.serviceName(), start, providerName);
    }
}
