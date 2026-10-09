package sg.nus.carelink.notification.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.notification.application.NotificationService;
import sg.nus.carelink.notification.domain.model.Inbox;
import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.web.GlobalExceptionHandlerTestSupport;

/** HTTP surface only: paths, shapes, status codes, and which role reads under the family rule. */
class NotificationControllerTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 7, 9, 0, 5);

	private final NotificationService service = mock(NotificationService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
			.setControllerAdvice(GlobalExceptionHandlerTestSupport.instance())
			.build();

	@Test
	void returns200WithTheRecord() throws Exception {
		when(service.findNotification(1L)).thenReturn(Optional.of(message(1L, Notification.Status.SENT)));

		mvc.perform(get("/api/notifications/1")).andExpect(status().isOk());
	}

	@Test
	void returns404WhenMissing() throws Exception {
		when(service.findNotification(2L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/notifications/2")).andExpect(status().isNotFound());
	}

	@Test
	void aFamilyMembersInboxIsReadUnderTheFamilyRuleInTheContractsShape() throws Exception {
		when(service.inbox("fiona", true, Notification.Status.SENT, 1, 5)).thenReturn(new Inbox(
				List.of(new InboxItem(message(4L, Notification.Status.SENT), 7L)), 1, 5, 6));

		mvc.perform(get("/api/notifications/me").param("page", "1").param("size", "5").param("status", "SENT")
						.with(as("fiona", "FAMILY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(4))
				.andExpect(jsonPath("$.items[0].elderId").value(7))
				.andExpect(jsonPath("$.items[0].channel").value("IN_APP"))
				.andExpect(jsonPath("$.items[0].status").value("SENT"))
				.andExpect(jsonPath("$.items[0].resourceType").value("ROSTER_CHANGE"))
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-10-07T09:00:05+08:00"))
				.andExpect(jsonPath("$.items[0].readAt").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(6));
	}

	@Test
	void aFamilyMemberCannotAskForUndeliveredMessages() throws Exception {
		when(service.inbox("fiona", true, Notification.Status.PENDING, 0, 20))
				.thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Undelivered status is unavailable"));
		mvc.perform(get("/api/notifications/me").param("status", "PENDING").with(as("fiona", "FAMILY")))
				.andExpect(status().isBadRequest());

		verify(service).inbox("fiona", true, Notification.Status.PENDING, 0, 20);
	}

	@Test
	void staffReadTheirOwnInboxWithoutTheFamilyRule() throws Exception {
		when(service.inbox("alice", false, null, 0, 20)).thenReturn(new Inbox(List.of(), 0, 20, 0));

		mvc.perform(get("/api/notifications/me").with(as("alice", "MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void theBellAsksOnlyForTheCount() throws Exception {
		when(service.unreadCount("fiona", true)).thenReturn(3L);

		mvc.perform(get("/api/notifications/me/unread-count").with(as("fiona", "FAMILY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unread").value(3));
	}

	@Test
	void openingOneReturnsItReadAndSomebodyElsesIsNotFound() throws Exception {
		when(service.markRead(4L, "fiona", true)).thenReturn(new InboxItem(message(4L, Notification.Status.READ), 7L));
		when(service.markRead(5L, "fiona", true)).thenThrow(new ResourceNotFound("Notification", 5L));

		mvc.perform(post("/api/notifications/4/read").with(as("fiona", "FAMILY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READ"));
		mvc.perform(post("/api/notifications/5/read").with(as("fiona", "FAMILY")))
				.andExpect(status().isNotFound());
	}

	@Test
	void markingEverythingReadSaysHowMany() throws Exception {
		when(service.markAllRead("aisha", false)).thenReturn(4);

		mvc.perform(post("/api/notifications/me/read-all").with(as("aisha", "CAREGIVER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updated").value(4));
	}

	private static Notification message(Long id, Notification.Status status) {
		return new Notification(id, 2L, "ROSTER_CHANGE_SETTLED", Notification.Channel.IN_APP, "Visit moved", "body",
				"ROSTER_CHANGE", 9L, status, AT, AT, status == Notification.Status.READ ? AT : null);
	}

	private static RequestPostProcessor as(String username, String role) {
		return request -> {
			request.setUserPrincipal(new UsernamePasswordAuthenticationToken(username, null,
					List.of(new SimpleGrantedAuthority("ROLE_" + role))));
			return request;
		};
	}
}
