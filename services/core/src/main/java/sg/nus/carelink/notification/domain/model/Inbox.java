package sg.nus.carelink.notification.domain.model;

import java.util.List;

/**
 * One page of a person's in-app messages, newest first.
 *
 * @param items this page
 * @param page zero-based page number
 * @param size the page size asked for
 * @param totalElements every message the person may see, counted after the same filtering
 */
public record Inbox(List<InboxItem> items, int page, int size, long totalElements) {

	public Inbox {
		items = List.copyOf(items);
	}
}
