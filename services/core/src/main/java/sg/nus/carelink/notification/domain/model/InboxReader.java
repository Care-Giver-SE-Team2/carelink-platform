package sg.nus.carelink.notification.domain.model;

/**
 * Whose inbox is being read, and whether the family rule applies: a family member sees a message
 * about an elder only while bound to that elder - an ACTIVE binding that has not expired - however
 * long ago the message was written. Revoking a binding takes the old messages out of sight too.
 *
 * @param userId the signed-in account
 * @param family whether the account reads as a family member
 */
public record InboxReader(Long userId, boolean family) {
}
