package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreApiClients;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * core's internal API in the running application, over real HTTP: its chain sits in front of
 * core's own chain without changing it, a call needs no login, no session and no CSRF token, and
 * core's errors reach the caller as the client library's exceptions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"carelink.report.schedule-cron=-",
		"carelink.escalation.scan-initial-delay=PT1H"})
class InternalApiIT {

	private static final long NO_SUCH_ID = 999_999L;

	@LocalServerPort
	private int port;

	private RestClient http;

	private CoreApi core;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, InternalApiIT.class, "+08:00", "connectionTimeZone=Asia/Singapore");
	}

	@BeforeEach
	void clients() {
		http = RestClient.builder().baseUrl("http://localhost:" + port).build();
		core = CoreApiClients.create(RestClient.builder().baseUrl("http://localhost:" + port));
	}

	@Test
	void aReadNeedsNoLoginAndLeavesNoSession() {
		ResponseEntity<String> response = http.get()
				.uri("/internal/v1/elders/{elderId}/family-members", NO_SUCH_ID)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("[]");
		assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void aChangeNeedsNoCsrfTokenAndCoresErrorsComeBackAsTheClientsExceptions() {
		assertThatThrownBy(() -> core.cover(NO_SUCH_ID, new CoreApi.CoverRequest(1L, 1L)))
				.isInstanceOf(CoreNotFound.class)
				.hasMessage("Visit [999999] does not exist");
		assertThatThrownBy(() -> core.checkElderAccess("no-such-family", 1L, CoreApi.Access.READ))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(core.findPrimaryCaregiverId(NO_SUCH_ID)).isEmpty();
	}

	@Test
	void corePublicApiStillNeedsALogin() {
		assertThatThrownBy(() -> http.get().uri("/api/family/elders").retrieve().toBodilessEntity())
				.isInstanceOf(HttpClientErrorException.Unauthorized.class);
	}

}
