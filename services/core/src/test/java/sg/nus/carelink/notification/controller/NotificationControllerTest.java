package sg.nus.carelink.notification.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.notification.application.NotificationService;
import sg.nus.carelink.notification.domain.model.Notification;

/** HTTP surface only: status codes for found and not found. Security is tested at the filter-chain level. */
class NotificationControllerTest {

	private final NotificationService service = mock(NotificationService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service)).build();

	@Test
	void returns200WithTheRecord() throws Exception {
		when(service.findNotification(1L)).thenReturn(Optional.of(new Notification(
				1L,
				2L,
				"v3",
				Notification.Channel.IN_APP,
				"v5",
				"v6",
				"v7",
				8L,
				Notification.Status.PENDING,
				LocalDateTime.of(2026, 9, 6, 10, 10),
				LocalDateTime.of(2026, 9, 6, 10, 11),
				LocalDateTime.of(2026, 9, 6, 10, 12))));

		mvc.perform(get("/api/notifications/1")).andExpect(status().isOk());
	}

	@Test
	void returns404WhenMissing() throws Exception {
		when(service.findNotification(2L)).thenReturn(Optional.empty());

		mvc.perform(get("/api/notifications/2")).andExpect(status().isNotFound());
	}
}
