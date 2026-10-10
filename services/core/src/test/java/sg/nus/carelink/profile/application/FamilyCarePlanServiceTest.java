package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Slot;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Task;

class FamilyCarePlanServiceTest {

	private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
	private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

	private final CarePlanSchedules schedules = mock(CarePlanSchedules.class);
	private final FamilyAccessQuery access = mock(FamilyAccessQuery.class);
	private final FamilyReadAudit audit = mock(FamilyReadAudit.class);
	// 10 Oct 2026 in Singapore.
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T02:00:00Z"), ZoneId.of("UTC"));
	private final FamilyCarePlanService service = new FamilyCarePlanService(schedules, access, audit, clock);

	@BeforeEach
	@SuppressWarnings("unchecked")
	void runAuditedQueries() {
		when(audit.read(eq("family"), eq(FamilyReadAudit.Resource.CARE_PLAN), eq(5L), eq(""), any()))
				.thenAnswer(call -> ((Supplier<Object>) call.getArgument(4)).get());
	}

	@Test
	void showsTheVersionInForceAndOnePublishedToStartLater() {
		when(schedules.forElder(5L)).thenReturn(List.of(
				plan(1, LocalDate.of(2026, 9, 1), OCT_1, "Grooming"),
				plan(2, OCT_1, NOV_3, "Bathing assistance"),
				plan(3, NOV_3, null, "Vital-sign check")));

		FamilyCarePlan view = service.view("family", 5L);

		assertThat(view.current().version()).isEqualTo(2);
		assertThat(view.current().tasks()).extracting(FamilyCarePlan.Task::name).containsExactly("Bathing assistance");
		assertThat(view.current().tasks().getFirst().slots())
				.containsExactly(new FamilyCarePlan.Slot(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30));
		assertThat(view.upcoming().version()).isEqualTo(3);
		assertThat(view.upcoming().effectiveFrom()).isEqualTo(NOV_3);
	}

	@Test
	void hasNothingToShowForAnElderWithoutAPlan() {
		FamilyCarePlan view = service.view("family", 5L);

		assertThat(view.current()).isNull();
		assertThat(view.upcoming()).isNull();
	}

	@Test
	void checksAccessBeforeReadingThePlan() {
		doThrow(new AccessDeniedException("Denied")).when(access).requireReadableElder("family", 5L);

		assertThatThrownBy(() -> service.view("family", 5L)).isInstanceOf(AccessDeniedException.class);
		verify(schedules, never()).forElder(any());
	}

	private static PlanSchedule plan(int version, LocalDate from, LocalDate until, String task) {
		return new PlanSchedule((long) version, 5L, version, from, until, List.of(new Task((long) version * 10,
				"Personal care", null, task, List.of(new Slot(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30)))));
	}
}
