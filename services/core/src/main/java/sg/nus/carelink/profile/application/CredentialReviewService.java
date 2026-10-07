package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.profile.domain.service.CredentialRegisterPolicy;
import sg.nus.carelink.profile.domain.service.CredentialRegisterPolicy.Entry;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * UC-MG06: the manager's certification register, and reviewing what caregivers submit from
 * the caregiver app — publish it or reject it. Which rows show and in
 * what state is CredentialRegisterPolicy; whether a review is allowed is Credential. This class
 * loads, resolves names and saves. The reviewer and time are kept on the credential itself.
 */
@Service
@Transactional
public class CredentialReviewService implements CredentialRegister {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

	private final CredentialRepository credentials;
	private final CredentialTypeRepository types;
	private final CaregiverRepository caregivers;
	private final Clock clock;
	private final CredentialRegisterPolicy policy;

	public CredentialReviewService(CredentialRepository credentials, CredentialTypeRepository types,
			CaregiverRepository caregivers, Clock clock,
			@Value("${carelink.caregiver.credential-warning-days:30}") int warningDays) {
		this.credentials = credentials;
		this.types = types;
		this.caregivers = caregivers;
		this.clock = clock;
		this.policy = new CredentialRegisterPolicy(warningDays);
	}

	/** Every row of the register, submitted first, then soonest to lapse. */
	@Transactional(readOnly = true)
	public List<CredentialRegisterRow> register() {
		List<Entry> entries = entries();
		Set<Long> caregiverIds = entries.stream().map(e -> e.credential().caregiverId()).collect(Collectors.toSet());
		Set<Long> typeIds = entries.stream().map(e -> e.credential().credentialTypeId()).collect(Collectors.toSet());
		Map<Long, String> caregiverNames = caregivers.findByIds(caregiverIds).stream()
				.collect(Collectors.toMap(Caregiver::id, Caregiver::fullName));
		Map<Long, String> typeNames = types.findByIds(typeIds).stream()
				.collect(Collectors.toMap(CredentialType::id, CredentialType::name));
		return entries.stream().map(e -> toRow(e, caregiverNames, typeNames)).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Cover> covers() {
		return entries().stream()
				.filter(e -> e.coveredUntil() != null)
				.map(e -> new Cover(e.credential().id(), e.credential().caregiverId(),
						e.credential().credentialTypeId(), e.coveredUntil()))
				.toList();
	}

	/**
	 * Publishes a submitted certificate; a renewal supersedes the one it replaces.
	 *
	 * @throws ResourceNotFound if there is no such credential
	 * @throws sg.nus.carelink.shared.error.BusinessRuleViolation if it is not waiting for review,
	 *         or renews a certificate of another caregiver or type
	 */
	public void publish(Long credentialId, Long reviewerId) {
		Credential submitted = require(credentialId);
		Credential replaced = submitted.renewsCredentialId() == null ? null
				: credentials.findById(submitted.renewsCredentialId()).orElse(null);
		credentials.save(submitted.publish(replaced, reviewerId, now()));
	}

	/** Rejects a submitted certificate; {@code reason} is required. */
	public void reject(Long credentialId, Long reviewerId, String reason) {
		credentials.save(require(credentialId).reject(reviewerId, reason, now()));
	}

	private List<Entry> entries() {
		return policy.register(credentials.findAll(), LocalDate.now(clock.withZone(ZONE)));
	}

	private Credential require(Long credentialId) {
		return credentials.findById(credentialId).orElseThrow(() -> new ResourceNotFound("Credential", credentialId));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock.withZone(ZONE));
	}

	private static CredentialRegisterRow toRow(Entry e, Map<Long, String> caregiverNames, Map<Long, String> typeNames) {
		Credential c = e.credential();
		Credential replaces = e.replaces();
		return new CredentialRegisterRow(c.id(), c.caregiverId(),
				caregiverNames.getOrDefault(c.caregiverId(), "Caregiver " + c.caregiverId()),
				c.credentialTypeId(), typeNames.getOrDefault(c.credentialTypeId(), "Credential"),
				c.renewsCredentialId() != null, e.state().name(), e.watchedExpiry(), e.daysUntilExpiry(), e.expiring(),
				c.certificateNo(), c.issuingBody(), c.validFrom(), permanentAsNull(c.expiryDate()), c.createdAt(),
				c.reviewNote(), c.reviewedAt(),
				replaces == null ? null : replaces.id(), replaces == null ? null : permanentAsNull(replaces.expiryDate()));
	}

	private static LocalDate permanentAsNull(LocalDate expiry) {
		return Credential.PERMANENT.equals(expiry) ? null : expiry;
	}
}
