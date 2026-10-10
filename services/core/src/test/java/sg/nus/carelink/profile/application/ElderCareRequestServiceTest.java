package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Task;
import sg.nus.carelink.profile.domain.model.ElderBasicDetails;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.ServiceApplication;
import sg.nus.carelink.profile.domain.repository.ServiceApplicationRepository;
import sg.nus.carelink.profile.domain.service.ServiceApplicationProgress.Outcome;

class ElderCareRequestServiceTest {

	private final InMemoryIntakeApplicationRepository intakes = new InMemoryIntakeApplicationRepository();
	private final ServiceApplicationRepository serviceApplications = mock(ServiceApplicationRepository.class);
	private final CarePlanSchedules schedules = mock(CarePlanSchedules.class);
	private final ElderCareRequestService service = new ElderCareRequestService(intakes, serviceApplications,
			new CareRequestProgress(schedules));

	@Test
	void combinesTheApprovedIntakeAndLaterServiceApplicationsNewestFirst() {
		intakes.save(intake(IntakeApplication.Status.APPROVED, 5L, List.of("BATHING", "VITALS")));
		intakes.save(intake(IntakeApplication.Status.REJECTED, 5L, List.of("GROOMING")));
		intakes.save(intake(IntakeApplication.Status.APPROVED, 6L, List.of("MEAL_SUPPORT")));
		when(serviceApplications.findByElderId(5L)).thenReturn(List.of(new ServiceApplication(9L, 42L, 5L,
				new ElderBasicDetails("Tan Mei", null, null, null, "Road", "123456", null, null, null),
				List.of("LIGHT_EXERCISE"), "Afternoons", ServiceApplication.Status.SUBMITTED,
				LocalDateTime.of(2026, 10, 1, 9, 0))));

		List<ElderCareRequest> requests = service.listForElder(5L);

		assertThat(requests).extracting(ElderCareRequest::source).containsExactly(
				ElderCareRequest.Source.SERVICE_APPLICATION, ElderCareRequest.Source.INTAKE);
		assertThat(requests.get(0).careNeeds()).containsExactly("LIGHT_EXERCISE");
		assertThat(requests.get(0).notes()).isEqualTo("Afternoons");
		assertThat(requests.get(1).careNeeds()).containsExactly("BATHING", "VITALS");
		assertThat(requests.get(1).notes()).isEqualTo("Diabetic");
	}

	@Test
	void anElderWithNoApplicationsHasNoRequests() {
		assertThat(service.listForElder(5L)).isEmpty();
	}

	@Test
	void saysWhichRequestsTheCarePlanAnswersAndWhichWereDeclined() {
		intakes.save(intake(IntakeApplication.Status.APPROVED, 5L, List.of("BATHING", "VITALS")));
		when(schedules.forElder(5L)).thenReturn(List.of(new PlanSchedule(70L, 5L, 1, LocalDate.of(2026, 9, 20), null,
				List.of(new Task(1L, "Personal care", "BATHING", "Bathing assistance", List.of()),
						new Task(2L, "Health monitoring", "VITALS", "Vital-sign check", List.of())))));
		when(serviceApplications.findByElderId(5L)).thenReturn(List.of(new ServiceApplication(9L, 42L, 5L,
				new ElderBasicDetails("Tan Mei", null, null, null, "Road", "123456", null, null, null),
				List.of("LIGHT_EXERCISE"), null, ServiceApplication.Status.DECLINED, LocalDateTime.of(2026, 10, 1, 9, 0),
				new ServiceApplication.Decline("No caregiver for exercise in your sector yet", 3L,
						LocalDateTime.of(2026, 10, 2, 9, 0)))));

		List<ElderCareRequest> requests = service.listForElder(5L);

		assertThat(requests.get(0).outcome()).isEqualTo(Outcome.DECLINED);
		assertThat(requests.get(0).declineReason()).isEqualTo("No caregiver for exercise in your sector yet");
		assertThat(requests.get(1).outcome()).isEqualTo(Outcome.PLANNED);
		assertThat(requests.get(1).needs()).allSatisfy(need -> assertThat(need.plannedVersion()).isEqualTo(1));
	}

	private static IntakeApplication intake(IntakeApplication.Status status, Long elderId, List<String> needs) {
		return new IntakeApplication(null, 42L, "Tan Mei", 80, "Road", "123456",
				IntakeApplication.MobilityLevel.INDEPENDENT, null, needs, "Diabetic", status, 1L, null,
				null, LocalDateTime.of(2026, 9, 16, 9, 0), elderId);
	}
}
