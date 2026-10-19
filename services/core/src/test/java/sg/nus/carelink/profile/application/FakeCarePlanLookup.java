package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.careplan.application.CarePlanLookup;
import sg.nus.carelink.careplan.domain.model.CarePlan;

/**
 * Test double for the cross-module contract: lets ProfileServiceTest control
 * each elder's latest plan without reaching into careplan's domain or infrastructure.
 */
class FakeCarePlanLookup implements CarePlanLookup {

    private final Map<Long, CarePlan> latestByElderId = new HashMap<>();
    private final Map<Long, CarePlan> publishedByElderId = new HashMap<>();
    private final Map<Long, LocalDate> nextVisitByElderId = new HashMap<>();

    void put(Long elderId, CarePlan plan) {
        latestByElderId.put(elderId, plan);
    }

    /** The version still in force while the latest one is a draft revising it. */
    void putPublished(Long elderId, CarePlan plan) {
        publishedByElderId.put(elderId, plan);
    }

    void putNextVisit(Long elderId, LocalDate nextVisitDate) {
        nextVisitByElderId.put(elderId, nextVisitDate);
    }

    @Override
    public Optional<CarePlan> findLatestByElderId(Long elderId) {
        return Optional.ofNullable(latestByElderId.get(elderId));
    }

    @Override
    public Optional<CarePlan> findPublishedByElderId(Long elderId) {
        CarePlan published = publishedByElderId.get(elderId);
        if (published != null) {
            return Optional.of(published);
        }
        return findLatestByElderId(elderId).filter(plan -> plan.status() == CarePlan.Status.PUBLISHED);
    }

    @Override
    public Optional<LocalDate> findNextVisitDate(Long elderId, LocalDate from) {
        return Optional.ofNullable(nextVisitByElderId.get(elderId));
    }
}
