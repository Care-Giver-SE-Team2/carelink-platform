package sg.nus.carelink.notification.domain.model;

/**
 * A message as its recipient sees it, with the elder it is about.
 *
 * @param notification the message
 * @param elderId the elder of the incident, roster change or spot check it links to; null for a
 *        message about the account itself
 */
public record InboxItem(Notification notification, Long elderId) {
}
