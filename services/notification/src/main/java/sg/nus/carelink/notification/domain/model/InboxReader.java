package sg.nus.carelink.notification.domain.model;

import java.util.Set;

/**
 * Whose inbox is being read, and whether the family rule applies: a family member sees a message
 * about an elder only while bound to that elder - an ACTIVE binding that has not expired - however
 * long ago the message was written. Revoking a binding takes the old messages out of sight too.
 *
 * @param userId the signed-in account
 * @param family whether the account reads as a family member
 * @param readableElders for a family member, the elders core says they may read now
 */
public record InboxReader(Long userId, boolean family, Set<Long> readableElders) {

	public InboxReader {
		readableElders = readableElders == null ? Set.of() : Set.copyOf(readableElders);
	}

	/** A reader whose inbox is their own: staff, an elder. */
	public static InboxReader own(Long userId) {
		return new InboxReader(userId, false, Set.of());
	}

	/** A family member, who sees a message about care while they may read its elder. */
	public static InboxReader family(Long userId, Set<Long> readableElders) {
		return new InboxReader(userId, true, readableElders);
	}
}
