package sg.nus.carelink.rostering.domain.service;

/**
 * A caregiver's recent spot-check conclusions (UC-MG08), as rostering may use them: a reason to
 * prefer somebody, never on its own a reason to exclude them.
 */
public record SpotCheckRecord(int metStandard, int needsImprovement) {

	public static final SpotCheckRecord NONE = new SpotCheckRecord(0, 0);

	public SpotCheckRecord {
		if (metStandard < 0 || needsImprovement < 0) {
			throw new IllegalArgumentException("Counts cannot be negative");
		}
	}

	public boolean isEmpty() {
		return metStandard == 0 && needsImprovement == 0;
	}
}
