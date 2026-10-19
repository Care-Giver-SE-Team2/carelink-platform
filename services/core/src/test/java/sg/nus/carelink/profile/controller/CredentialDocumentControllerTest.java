package sg.nus.carelink.profile.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import sg.nus.carelink.profile.application.CaregiverCredentialService;
import sg.nus.carelink.profile.application.CaregiverCredentialService.Content;
import sg.nus.carelink.profile.application.CaregiverCredentialService.DocumentView;

/** HTTP surface only: the headers a scan is served with. Access checks live in CaregiverCredentialService. */
class CredentialDocumentControllerTest {

	private final CaregiverCredentialService service = mock(CaregiverCredentialService.class);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CredentialDocumentController(service)).build();
	private final TestingAuthenticationToken manager = new TestingAuthenticationToken("tan.meiling", null);

	@Test
	void servesTheScanInlineSoTheBrowserShowsIt() throws Exception {
		byte[] bytes = {1, 2, 3};
		when(service.content("tan.meiling", 57L, 3L)).thenReturn(new Content(new DocumentView(3L, 57L, "first aid.pdf",
				"application/pdf", 3, LocalDateTime.of(2026, 10, 13, 19, 0), true, "/api/credentials/57/documents/3/content"), bytes));

		mvc.perform(get("/api/credentials/57/documents/3/content").principal(manager))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(content().bytes(bytes))
				.andExpect(header().string("Content-Disposition", "inline; filename=\"first aid.pdf\"; filename*=UTF-8''first%20aid.pdf"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
				.andExpect(header().string("Cache-Control", "no-store"));
	}
}
