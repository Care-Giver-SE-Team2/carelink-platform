package sg.nus.carelink.incident.application;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cross-module contract for UC-MG08's rule that a spot-check conclusion "进入排班决策，但不单独作为
 * 处罚依据": rostering reads each caregiver's recent conclusions and weighs them as one soft
 * signal among several. Rostering imports this interface only.
 */
public interface SpotCheckHistory {

	/** Caregiver id to their conclusions recorded since then; caregivers with none are absent. */
	Map<Long, Conclusions> recentConclusions(LocalDateTime since);

	record Conclusions(int metStandard, int needsImprovement) {
	}
}
