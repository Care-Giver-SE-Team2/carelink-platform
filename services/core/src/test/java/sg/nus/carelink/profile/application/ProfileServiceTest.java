package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.shared.error.ResourceNotFound;

class ProfileServiceTest {

        private final InMemoryElderRepository repository = new InMemoryElderRepository();

        private final FakeCarePlanLookup carePlans = new FakeCarePlanLookup();

        private final InMemoryPrimaryCaregiverAssignmentRepository primaryCaregivers =
                        new InMemoryPrimaryCaregiverAssignmentRepository();

        private final InMemoryCaregiverRepository caregivers = new InMemoryCaregiverRepository();

        private final ProfileService service =
                        new ProfileService(repository, carePlans, primaryCaregivers, caregivers);

        @Test
        void findsWhatWasSaved() {
                Elder saved = saveElder(2L);

                assertThat(service.findElder(saved.id()))
                                .contains(saved);
        }

        @Test
        void isEmptyForAnUnknownId() {
                assertThat(service.findElder(999L))
                                .isEmpty();
        }

        @Test
        void findsElderByLinkedUserId() {
                Elder saved = saveElder(7L);

                Elder found = service.requireElderByUserId(7L);

                assertThat(found)
                                .isEqualTo(saved);

                assertThat(found.userId())
                                .isEqualTo(7L);
        }

        @Test
        void throwsWhenNoElderIsLinkedToUser() {
                assertThatThrownBy(
                                () -> service.requireElderByUserId(999L))
                                .isInstanceOf(ResourceNotFound.class)
                                .hasMessageContaining("Elder for user")
                                .hasMessageContaining("999");
        }

        @Test
        void listsEldersWithNoPlanAsNone() {
                Elder saved = saveElder(1L);

                List<ElderSummary> summaries = service.listElders();

                assertThat(summaries)
                                .containsExactly(new ElderSummary(saved, "none", null, null));
        }

        @Test
        void listsTheAssignedPrimaryCaregiverAndLeavesOthersNull() {
                Elder assigned = saveElder(1L);
                Elder unassigned = saveElder(2L);
                Caregiver aisyah = caregivers.save("Aisyah N.", Caregiver.Status.AVAILABLE);
                LocalDateTime since = LocalDateTime.of(2026, 9, 20, 9, 0);
                primaryCaregivers.save(new PrimaryCaregiverAssignment(assigned.id(), aisyah.id(), since));

                List<ElderSummary> summaries = service.listElders();

                assertThat(summaries).containsExactlyInAnyOrder(
                                new ElderSummary(assigned, "none", null, null,
                                                new PrimaryCaregiverSummary(aisyah.id(), "Aisyah N.", since)),
                                new ElderSummary(unassigned, "none", null, null));
        }

        @Test
        void listsEldersWithAPublishedPlan() {
                Elder saved = saveElder(2L);
                carePlans.put(saved.id(), plan(saved.id(), CarePlan.Status.PUBLISHED, 3));
                carePlans.putNextVisit(saved.id(), LocalDate.of(2026, 9, 25));

                List<ElderSummary> summaries = service.listElders();

                assertThat(summaries)
                                .containsExactly(new ElderSummary(saved, "published", 3, LocalDate.of(2026, 9, 25)));
        }

        @Test
        void listsEldersWithADraftPlan() {
                Elder saved = saveElder(3L);
                carePlans.put(saved.id(), plan(saved.id(), CarePlan.Status.DRAFT, 1));

                List<ElderSummary> summaries = service.listElders();

                assertThat(summaries)
                                .containsExactly(new ElderSummary(saved, "draft", 1, null));
        }

        @Test
        void listsEldersWithASupersededPlanAsNone() {
                Elder saved = saveElder(4L);
                carePlans.put(saved.id(), plan(saved.id(), CarePlan.Status.SUPERSEDED, 1));

                List<ElderSummary> summaries = service.listElders();

                assertThat(summaries)
                                .containsExactly(new ElderSummary(saved, "none", null, null));
        }

        @Test
        void listsEldersWithAStoppedPlanAsStopped() {
                Elder saved = saveElder(5L);
                carePlans.put(saved.id(), plan(saved.id(), CarePlan.Status.STOPPED, 2));
                carePlans.putNextVisit(saved.id(), LocalDate.of(2026, 9, 25));

                List<ElderSummary> summaries = service.listElders();

                assertThat(summaries)
                                .containsExactly(new ElderSummary(saved, "stopped", 2, null));
        }

        private static CarePlan plan(Long elderId, CarePlan.Status status, int version) {
                return new CarePlan(
                                100L + version,
                                elderId,
                                1L,
                                null,
                                version,
                                status,
                                BigDecimal.TEN,
                                LocalDateTime.of(2026, 9, 6, 9, 0),
                                LocalDateTime.of(2026, 9, 6, 9, 0),
                                LocalDateTime.of(2026, 9, 6, 9, 0));
        }

        private Elder saveElder(Long userId) {
                return repository.save(
                                new Elder(
                                                null,
                                                userId,
                                                "v3",
                                                Elder.Gender.MALE,
                                                LocalDate.of(2026, 9, 6),
                                                "v6",
                                                "v7",
                                                "v8",
                                                "v9",
                                                "v10",
                                                Boolean.TRUE,
                                                Elder.MobilityLevel.INDEPENDENT,
                                                Elder.ContinuityPreference.PREFERRED,
                                                "v14",
                                                LocalDateTime.of(
                                                                2026,
                                                                9,
                                                                6,
                                                                10,
                                                                15),
                                                LocalDateTime.of(
                                                                2026,
                                                                9,
                                                                6,
                                                                10,
                                                                16)));
        }
}
