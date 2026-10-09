package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.application.SpotCheckHistory;
import sg.nus.carelink.profile.application.CredentialRegister;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.service.ReplacementFinder;
import sg.nus.carelink.rostering.domain.service.ReplacementRules;
import sg.nus.carelink.rostering.domain.service.RosterSnapshot;
import sg.nus.carelink.rostering.domain.service.ScoringObjective;
import sg.nus.carelink.rostering.domain.service.Shortlist;

/**
 * What the search is given to read: spot-check conclusions (UC-MG08) from the incident module,
 * over the configured lookback, reach the SPOT_CHECK rule - a reason to prefer somebody, never
 * on its own a reason to exclude them.
 */
class RosterSnapshotLoaderTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime EIGHTH = LocalDateTime.of(2026, 10, 8, 9, 0);

	@Test
	void recentSpotCheckConclusionsReachTheSearchButExcludeNobody() {
		ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles()
				.caregiver(9L, "Farah", false).caregiver(10L, "Siti", false).elder(7L, "Mdm Tan");
		ReRosteringFakes.Visits visits = new ReRosteringFakes.Visits();
		AtomicReference<LocalDateTime> askedSince = new AtomicReference<>();
		SpotCheckHistory history = since -> {
			askedSince.set(since);
			return Map.of(10L, new SpotCheckHistory.Conclusions(0, 2), 9L, new SpotCheckHistory.Conclusions(3, 0));
		};
		RosterSnapshotLoader loader = new RosterSnapshotLoader(profiles, () -> List.<CredentialRegister.Cover>of(),
				planIds -> Map.of(), new ReRosteringFakes.Absences(), visits, history, RosterLookbacks.DEFAULT,
				new ReRosteringFakes.MutableClock(NOW, ZoneId.of("Asia/Singapore")));
		VacatedSlot slot = new VacatedSlot(30L, 7L, null, null, EIGHTH, EIGHTH.plusMinutes(45), 5L);

		RosterSnapshot snapshot = loader.load(List.of(slot));
		Shortlist shortlist = new ReplacementFinder(ReplacementRules.defaults(),
				ScoringObjective.of(null)).shortlist(slot, snapshot);

		assertThat(askedSince.get()).isEqualTo(NOW.minusDays(90));
		assertThat(snapshot.spotChecks(10L).needsImprovement()).isEqualTo(2);
		assertThat(shortlist.ranked()).extracting(Shortlist.Verdict::caregiverId).containsExactly(9L, 10L);
		assertThat(shortlist.excluded()).isEmpty();
		assertThat(loader.load(List.of()).candidates()).isEmpty();
	}
}
