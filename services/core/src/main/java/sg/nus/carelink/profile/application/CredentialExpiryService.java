package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.domain.repository.CaregiverRepository;
import sg.nus.carelink.profile.domain.repository.CredentialExpiryAlert;
import sg.nus.carelink.profile.domain.repository.CredentialRepository;
import sg.nus.carelink.profile.domain.repository.CredentialTypeRepository;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Lapse;

/**
 * SYS01: the daily qualification-expiry scan. Which certificates move and who hears is
 * CredentialExpiryScan; this class loads, saves the new statuses and hands each one to the
 * alert. One transaction for the whole run: a failure leaves every status where it was, and
 * since the rule only reads dates, the next run picks up the same certificates again.
 */
@Service
@Transactional
public class CredentialExpiryService {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

	private final CredentialRepository credentials;
	private final CredentialTypeRepository types;
	private final CaregiverRepository caregivers;
	private final CredentialExpiryAlert alert;
	private final Clock clock;
	private final CredentialExpiryScan scan;

	public CredentialExpiryService(CredentialRepository credentials, CredentialTypeRepository types,
			CaregiverRepository caregivers, CredentialExpiryAlert alert, Clock clock,
			@Value("${carelink.caregiver.credential-warning-days:30}") int warningDays) {
		this.credentials = credentials;
		this.types = types;
		this.caregivers = caregivers;
		this.alert = alert;
		this.clock = clock;
		this.scan = new CredentialExpiryScan(warningDays);
	}

	/** Moves today's certificates on and tells whoever needs to know. */
	public Result scanToday() {
		List<Lapse> lapses = scan.scan(credentials.findAll(), LocalDate.now(clock.withZone(ZONE)));
		Map<Long, Caregiver> caregiverById = caregivers.findByIds(ids(lapses, c -> c.credential().caregiverId()))
				.stream().collect(Collectors.toMap(Caregiver::id, Function.identity()));
		Map<Long, String> typeNames = types.findByIds(ids(lapses, c -> c.credential().credentialTypeId()))
				.stream().collect(Collectors.toMap(CredentialType::id, CredentialType::name));

		int told = 0;
		for (Lapse lapse : lapses) {
			credentials.save(lapse.credential());
			Caregiver caregiver = caregiverById.get(lapse.credential().caregiverId());
			if (caregiver != null) {
				told += alert.lapsed(lapse, caregiver,
						typeNames.getOrDefault(lapse.credential().credentialTypeId(), "Credential"));
			}
		}
		int expired = (int) lapses.stream().filter(Lapse::expired).count();
		return new Result(lapses.size() - expired, expired, told);
	}

	private static Set<Long> ids(List<Lapse> lapses, Function<Lapse, Long> id) {
		return lapses.stream().map(id).collect(Collectors.toSet());
	}

	/**
	 * @param expiring certificates that entered the warning window today
	 * @param expired certificates that ran out
	 * @param notified notifications written
	 */
	public record Result(int expiring, int expired, int notified) {
	}
}
