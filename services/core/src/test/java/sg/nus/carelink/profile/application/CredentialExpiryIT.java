package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * SYS01 end to end on real MySQL: the scan stores the new statuses, asks for the alerts for the
 * right people (NotificationRequested events, which notification turns into inbox messages), and
 * says nothing new on a second run the same day. Today is 2026-10-04 in Singapore.
 */
@SpringBootTest
@Transactional
@Import(CredentialExpiryIT.FixedTime.class)
class CredentialExpiryIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CredentialExpiryIT.class, null);
	}

	@Autowired
	private CredentialExpiryService service;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private EntityManager entityManager;

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock expiryClock() {
			return Clock.fixed(Instant.parse("2026-10-03T17:15:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void seed() {
		jdbc.update("insert into app_user(id,username,password_hash,display_name) values"
				+ " (8701,'exp-manager','unused','Manager'),(8702,'exp-devi','unused','Devi'),(8703,'exp-rosnah','unused','Rosnah')");
		jdbc.update("insert into user_role(user_id,role) values (8701,'MANAGER'),(8702,'CAREGIVER'),(8703,'CAREGIVER')");
		jdbc.update("insert into caregiver(id,user_id,full_name,status) values"
				+ " (8711,8702,'Devi Raman','AVAILABLE'),(8712,8703,'Rosnah Binte Ali','AVAILABLE')");
		jdbc.update("insert into credential_type(id,name) values (8721,'First aid SYS01'),(8722,'Dementia care SYS01')");
		jdbc.update("insert into credential(id,caregiver_id,credential_type_id,certificate_no,expiry_date,status,renews_credential_id) values"
				// Devi: first aid inside the window with nothing submitted; dementia care lapsed yesterday.
				+ " (8731,8711,8721,'FA-D','2026-10-20','PUBLISHED',null),"
				+ " (8732,8711,8722,'DC-D','2026-10-03','EXPIRING',null),"
				// Rosnah: inside the window, but a renewal is waiting for review.
				+ " (8733,8712,8721,'FA-R','2026-10-10','PUBLISHED',null),"
				+ " (8734,8712,8721,'FA-R2','2028-10-10','SUBMITTED',8733),"
				// Rosnah: well clear of the window.
				+ " (8735,8712,8722,'DC-R','2027-06-01','PUBLISHED',null)");
	}

	@Test
	void storesTheNewStatusesAndTellsTheRightPeopleOnce() {
		var first = service.scanToday();
		entityManager.flush();

		assertThat(status(8731)).isEqualTo("EXPIRING");
		assertThat(status(8732)).isEqualTo("EXPIRED");
		assertThat(status(8733)).isEqualTo("EXPIRING");
		assertThat(status(8735)).isEqualTo("PUBLISHED");

		assertThat(recipients(8731)).contains(8701L, 8702L);
		assertThat(recipients(8732)).contains(8701L, 8702L);
		assertThat(recipients(8733)).contains(8701L).doesNotContain(8703L);
		assertThat(recipients(8735)).isEmpty();

		Map<String, Object> devisAlert = jdbc.queryForMap("select json_unquote(json_extract(payload, '$.kind')) as kind,"
				+ " json_unquote(json_extract(payload, '$.title')) as title,"
				+ " json_unquote(json_extract(payload, '$.channel')) as channel from outbox_event" + CREDENTIAL_ALERTS
				+ " and json_unquote(json_extract(payload, '$.resourceId')) = '8732'"
				+ " and json_unquote(json_extract(payload, '$.recipientUserId')) = '8702'");
		assertThat(devisAlert).containsEntry("kind", "CREDENTIAL_EXPIRED")
				.containsEntry("title", "Your Dementia care SYS01 certificate expired on 3 Oct 2026")
				.containsEntry("channel", "IN_APP");
		assertThat(first.expiring()).isGreaterThanOrEqualTo(2);
		assertThat(first.expired()).isGreaterThanOrEqualTo(1);

		int before = count();
		service.scanToday();
		entityManager.flush();
		assertThat(count()).isEqualTo(before);
	}

	private String status(long id) {
		return jdbc.queryForObject("select status from credential where id = ?", String.class, id);
	}

	/** The alerts the scan asked for: NotificationRequested events about a credential, in the outbox. */
	private static final String CREDENTIAL_ALERTS = " where type = 'NotificationRequested'"
			+ " and json_unquote(json_extract(payload, '$.resourceType')) = 'CREDENTIAL'";

	private List<Long> recipients(long credentialId) {
		return jdbc.queryForList("select cast(json_unquote(json_extract(payload, '$.recipientUserId')) as unsigned)"
				+ " from outbox_event" + CREDENTIAL_ALERTS + " and json_unquote(json_extract(payload, '$.resourceId')) = ?",
				Long.class, String.valueOf(credentialId));
	}

	private int count() {
		return jdbc.queryForObject("select count(*) from outbox_event" + CREDENTIAL_ALERTS
				+ " and cast(json_unquote(json_extract(payload, '$.resourceId')) as unsigned) between 8731 and 8735",
				Integer.class);
	}
}
