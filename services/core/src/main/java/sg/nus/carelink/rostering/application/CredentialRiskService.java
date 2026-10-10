package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.application.CarePlanRequirements;
import sg.nus.carelink.profile.application.CredentialRegister;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk;
import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;
import sg.nus.carelink.rostering.domain.repository.BookedVisits;

/**
 * UC-MG06: for each row of the manager's certification register, the booked visits it puts at
 * risk within the roster window. The register comes from profile, the bookings from visit (through
 * rostering's own port, {@link BookedVisits}) and each plan's required certifications from
 * careplan; the rule is VisitsAtRisk.
 */
@Service
@Transactional(readOnly = true)
public class CredentialRiskService {

	private final CredentialRegister register;
	private final BookedVisits bookedVisits;
	private final CarePlanRequirements requirements;
	private final Clock clock;
	private final int horizonDays;

	public CredentialRiskService(CredentialRegister register, BookedVisits bookedVisits,
			CarePlanRequirements requirements, Clock clock,
			@Value("${carelink.roster.horizon-days:14}") int horizonDays) {
		this.register = register;
		this.bookedVisits = bookedVisits;
		this.requirements = requirements;
		this.clock = clock;
		this.horizonDays = horizonDays;
	}

	/** One entry per register row that covers someone; {@code visitsAtRisk} null beyond the window. */
	public List<CredentialRisk> risks() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime windowEnd = now.toLocalDate().plusDays(horizonDays).atStartOfDay();
		List<Booking> bookings = bookedVisits.unstartedBetween(now, windowEnd);
		Set<Long> planIds = bookings.stream().map(Booking::carePlanId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		VisitsAtRisk atRisk = new VisitsAtRisk(now, windowEnd, bookings, requirements.requiredCredentialTypes(planIds));
		return register.covers().stream()
				.map(cover -> new CredentialRisk(cover.credentialId(),
						atRisk.count(cover.caregiverId(), cover.credentialTypeId(), cover.coveredUntil()).orElse(null)))
				.toList();
	}

	public record CredentialRisk(Long credentialId, Integer visitsAtRisk) {
	}
}
