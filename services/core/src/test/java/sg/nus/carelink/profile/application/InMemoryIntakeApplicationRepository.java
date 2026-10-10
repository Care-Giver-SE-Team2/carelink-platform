package sg.nus.carelink.profile.application;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeApplicationPage;
import sg.nus.carelink.profile.domain.repository.IntakeApplicationRepository;

/**
 * Stores intake applications in memory and assigns test identifiers and creation times.
 *
 * @author Wang Zhili
 */
public class InMemoryIntakeApplicationRepository implements IntakeApplicationRepository {

	private final Map<Long, IntakeApplication> rows = new HashMap<>();
	private long nextId = 1;

	@Override
	public Optional<IntakeApplication> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public List<IntakeApplication> findApprovedByElderId(Long elderId) {
		return rows.values().stream()
				.filter(row -> row.status() == IntakeApplication.Status.APPROVED && elderId.equals(row.elderId()))
				.toList();
	}

	@Override
	public IntakeApplicationPage findForApplicant(Long familyMemberId, IntakeApplication.Status status,
			int page, int size) {
		var matching = rows.values().stream()
				.filter(row -> row.applicantFamilyMemberId().equals(familyMemberId))
				.filter(row -> status == null || row.status() == status)
				.sorted(Comparator.comparing(IntakeApplication::createdAt).thenComparing(IntakeApplication::id).reversed())
				.toList();
		return new IntakeApplicationPage(matching.stream().skip((long) page * size).limit(size).toList(),
				page, size, matching.size());
	}

	/** Test setup only: the port no longer writes, since families can no longer submit one. */
	public IntakeApplication save(IntakeApplication application) {
		IntakeApplication stored = application.id() == null
				? new IntakeApplication(nextId++, application.applicantFamilyMemberId(),
						application.targetElderName(), application.targetElderAge(), application.targetAddress(),
						application.postalCode(), application.mobilityLevel(), application.preferredDialects(),
						application.careNeeds(), application.medicalNotes(), application.status(),
						application.reviewedByUserId(), application.reviewRemarks(),
						LocalDateTime.of(2026, 9, 15, 10, 0), application.reviewedAt(), application.elderId())
				: application;
		rows.put(stored.id(), stored);
		return stored;
	}
}
