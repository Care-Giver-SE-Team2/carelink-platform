package sg.nus.carelink.rostering.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * How long a family has to answer a change (UC-MG04 business rule: "家属响应时限由机构配置，默认
 * 2 小时，不是代码常量").
 *
 * <p>The window is cut short by the visit itself: whoever takes it has to be told in time to get
 * there, so the answer is due {@code lead} before the visit starts at the latest. When even that
 * moment has passed there is no time to ask, and the default plan runs at once.
 *
 * @param window how long the family is given, two hours unless the institution says otherwise
 * @param lead how long before the visit the replacement must know
 */
public record FamilyResponseWindow(Duration window, Duration lead) {

	public static final FamilyResponseWindow DEFAULT = new FamilyResponseWindow(Duration.ofHours(2), Duration.ofHours(1));

	public FamilyResponseWindow {
		Objects.requireNonNull(window, "window");
		Objects.requireNonNull(lead, "lead");
		if (window.isNegative() || window.isZero() || lead.isNegative()) {
			throw new IllegalArgumentException("The family window must be positive and the lead not negative");
		}
	}

	/** When the family must answer by, or empty when the visit is too close to ask at all. */
	public Optional<LocalDateTime> respondBy(LocalDateTime now, LocalDateTime visitStart) {
		LocalDateTime full = now.plus(window);
		LocalDateTime latest = visitStart.minus(lead);
		LocalDateTime due = full.isBefore(latest) ? full : latest;
		return due.isAfter(now) ? Optional.of(due) : Optional.empty();
	}
}
