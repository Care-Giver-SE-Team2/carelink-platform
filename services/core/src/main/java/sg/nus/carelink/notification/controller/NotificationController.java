package sg.nus.carelink.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.notification.application.NotificationService;
import sg.nus.carelink.notification.controller.dto.NotificationResponses;
import sg.nus.carelink.notification.domain.model.Notification;

/**
 * Presentation layer of the notification module: HTTP in, HTTP out, status codes. No business
 * rules. Talks to NotificationService only, never to a repository (ArchUnit enforces it).
 *
 * <p>The inbox endpoints follow the contract drafted for UC-FM05 ({@code /notifications/me} and
 * {@code /notifications/{id}/read}) and add the two the bell needs: the unread count and "mark
 * all read". They serve whoever is signed in, in any role, and only ever their own messages: the
 * account comes from the session, never from the request. The family rule applies to anybody
 * signed in with the FAMILY role.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	/** GET /api/notifications/me : my messages, newest first. A family member may filter SENT or READ only. */
	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public NotificationResponses.InboxPage inbox(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Notification.Status status,
			Authentication who) {
		return NotificationResponses.InboxPage.of(service.inbox(who.getName(), isFamily(who), status, page, size));
	}

	/** GET /api/notifications/me/unread-count : the number on the bell, polled. */
	@GetMapping("/me/unread-count")
	@PreAuthorize("isAuthenticated()")
	public NotificationResponses.UnreadCount unreadCount(Authentication who) {
		return new NotificationResponses.UnreadCount(service.unreadCount(who.getName(), isFamily(who)));
	}

	/** POST /api/notifications/{id}/read : I opened this one. 404 if it is not mine to see. */
	@PostMapping("/{id}/read")
	@PreAuthorize("isAuthenticated()")
	public NotificationResponses.Item markRead(@PathVariable Long id, Authentication who) {
		return NotificationResponses.Item.of(service.markRead(id, who.getName(), isFamily(who)));
	}

	/** POST /api/notifications/me/read-all : everything I may see is read. */
	@PostMapping("/me/read-all")
	@PreAuthorize("isAuthenticated()")
	public NotificationResponses.MarkedRead markAllRead(Authentication who) {
		return new NotificationResponses.MarkedRead(service.markAllRead(who.getName(), isFamily(who)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<Notification> get(@PathVariable Long id) {
		return ResponseEntity.of(service.findNotification(id));
	}

	private static boolean isFamily(Authentication who) {
		return who.getAuthorities().stream().anyMatch(authority -> "ROLE_FAMILY".equals(authority.getAuthority()));
	}
}
