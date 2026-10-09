package sg.nus.carelink.rostering.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UC-MG04's numbers, as the institution sets them under {@code carelink.rerostering}: the use
 * case says the family's time to answer is configured, two hours by default, and never a
 * constant in code. Defaults match {@code FamilyResponseWindow.DEFAULT}, so a deployment with
 * no configuration behaves as the use case describes.
 */
@ConfigurationProperties(prefix = "carelink.rerostering")
public class ReRosteringProperties {

	/** How long a family has to answer a change. */
	private Duration familyWindow = Duration.ofHours(2);

	/** How long before a visit its new caregiver must know; the family's answer is due by then. */
	private Duration decisionLead = Duration.ofHours(1);

	/** How far back a caregiver's finished visits count towards continuity. */
	private Duration continuityLookback = Duration.ofDays(180);

	/** How far back spot-check conclusions (UC-MG08) count in the search. */
	private Duration spotCheckLookback = Duration.ofDays(90);

	public Duration getFamilyWindow() {
		return familyWindow;
	}

	public void setFamilyWindow(Duration familyWindow) {
		this.familyWindow = familyWindow;
	}

	public Duration getDecisionLead() {
		return decisionLead;
	}

	public void setDecisionLead(Duration decisionLead) {
		this.decisionLead = decisionLead;
	}

	public Duration getContinuityLookback() {
		return continuityLookback;
	}

	public void setContinuityLookback(Duration continuityLookback) {
		this.continuityLookback = continuityLookback;
	}

	public Duration getSpotCheckLookback() {
		return spotCheckLookback;
	}

	public void setSpotCheckLookback(Duration spotCheckLookback) {
		this.spotCheckLookback = spotCheckLookback;
	}
}
