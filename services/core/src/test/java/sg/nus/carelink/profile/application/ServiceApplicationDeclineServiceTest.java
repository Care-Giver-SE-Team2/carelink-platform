package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Task;
import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.repository.ServiceApplicationRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

class ServiceApplicationDeclineServiceTest {

	private final ServiceApplicationRepository applications = mock(ServiceApplicationRepository.class);
	private final CarePlanSchedules schedules = mock(CarePlanSchedules.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T02:00:00Z"), ZoneId.of("Asia/Singapore"));
	private final ServiceApplicationDeclineService service = new ServiceApplicationDeclineService(
			applications, new CareRequestProgress(schedules), users, clock);
	private final ServiceApplication submitted = new ServiceApplication(9L, 42L, 5L,
			new ElderBasicDetails("Tan Mei", null, null, null, "Road", "123456", null, null, null),
			List.of("LIGHT_EXERCISE"), null, ServiceApplication.Status.SUBMITTED, LocalDateTime.of(2026, 10, 1, 9, 0));

	@BeforeEach
	void setup() {
		when(applications.findById(9L)).thenReturn(Optional.of(submitted));
		when(applications.save(any())).thenAnswer(call -> call.getArgument(0));
		when(users.findByUsername("mei.ling"))
				.thenReturn(Optional.of(new AppUser(3L, "mei.ling", "Tan Mei Ling", Set.of(Role.MANAGER), true)));
	}

	@Test
	void recordsWhoDeclinedItWhenAndWhy() {
		ServiceApplication declined = service.decline(9L, "No exercise coach in your sector yet", "mei.ling");

		assertThat(declined.status()).isEqualTo(ServiceApplication.Status.DECLINED);
		assertThat(declined.decline()).isEqualTo(new ServiceApplication.Decline(
				"No exercise coach in your sector yet", 3L, LocalDateTime.of(2026, 10, 10, 2, 0)));
	}

	@Test
	void refusesOneWhoseCareIsAlreadyPlanned() {
		when(schedules.forElder(5L)).thenReturn(List.of(new PlanSchedule(70L, 5L, 2, LocalDate.of(2026, 10, 5), null,
				List.of(new Task(1L, "Social and mobility", "LIGHT_EXERCISE", "Light exercise", List.of())))));

		assertThatThrownBy(() -> service.decline(9L, "Too late", "mei.ling"))
				.isInstanceOfSatisfying(BusinessRuleViolation.class,
						error -> assertThat(error.code()).isEqualTo("SERVICE_APPLICATION_ALREADY_PLANNED"));
		verify(applications, never()).save(any());
	}

	@Test
	void anUnknownApplicationIsNotFound() {
		assertThatThrownBy(() -> service.decline(99L, "Reason", "mei.ling")).isInstanceOf(ResourceNotFound.class);
	}
}
