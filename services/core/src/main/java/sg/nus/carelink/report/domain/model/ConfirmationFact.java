package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The elder's own answer about one visit of the period (UC-EL01): confirmed or disputed, and
 * the rating and comment that came with it.
 *
 * <p>A reading of elder_confirmation. There is a row only once the elder has answered; a visit
 * closed because nobody answered has none, and is not counted as rated.
 *
 * @param disputed true when the elder said the visit did not happen as recorded
 * @param rating   1 to 5, or null when the elder answered without rating
 * @param comment  in the elder's own words; may be null
 */
public record ConfirmationFact(Long visitId, boolean disputed, Integer rating, String comment, LocalDateTime confirmedAt) {

	public ConfirmationFact {
		Objects.requireNonNull(visitId, "visitId");
	}

	public boolean isRated() {
		return rating != null;
	}
}
