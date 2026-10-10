package sg.nus.carelink.report.application;

import java.util.List;

/** Who may take a visit over, and putting one of them on it. Rostering in core decides both. */
public interface VisitCover {

	/** Every caregiver considered for the visit, the eligible ones ranked. */
	List<Option> options(Long visitId);

	/** Puts the caregiver on the visit, as decided by the user. */
	void cover(Long visitId, Long caregiverId, Long byUserId);

	/** @param rank null when the caregiver is not eligible, and {@code reason} says why */
	record Option(Long caregiverId, String name, Integer rank, String reason) {

		public boolean eligible() {
			return rank != null;
		}

	}

}
