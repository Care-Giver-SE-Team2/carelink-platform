package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.*;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

class CaregiverApplicationTest {
    private final LocalDateTime now=LocalDateTime.of(2026,10,12,9,0);
    private final Details details=new Details("Demo Caregiver","+6591234567",null,null);
    private CaregiverApplication pending() { return new CaregiverApplication(1L,2L,Status.PENDING,1,details,now,null,null,null); }
    @Test void approvalRequiresNoCertificateAndPreservesSubmission() {
        var approved=pending().decide(true,9L,null,now.plusMinutes(1));
        assertThat(approved.status()).isEqualTo(Status.APPROVED);
        assertThat(approved.details()).isEqualTo(details);
        assertThat(approved.submittedAt()).isEqualTo(now);
        assertThat(approved.reviewedByUserId()).isEqualTo(9L);
        assertThat(approved.reviewedAt()).isEqualTo(now.plusMinutes(1));
    }
    @Test void rejectionRequiresANonblankReason() {
        assertThatThrownBy(() -> pending().decide(false,9L,"  ",now)).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> pending().decide(false,9L,null,now)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> pending().decide(false,9L,"x".repeat(501),now)).isInstanceOf(BusinessRuleViolation.class);
    }
    @Test void rejectedSubmissionCanBeEditedWithoutRewritingItsDecision() {
        var rejected=pending().decide(false,9L,"  Please correct your phone  ",now);
        var replacement=new Details("Updated Name","+6581234567","English","North");
        var edited=rejected.edit(replacement);
        assertThat(edited.status()).isEqualTo(Status.REJECTED);
        assertThat(edited.reviewReason()).isEqualTo("Please correct your phone");
        assertThat(edited.details()).isEqualTo(replacement);
        assertThat(rejected.details()).isEqualTo(details);
    }
    @Test void resubmissionAdvancesRoundAndClearsOnlyTheNewDecision() {
        var rejected=pending().decide(false,9L,"Correction needed",now);
        var next=rejected.resubmit(now.plusDays(1));
        assertThat(next.roundNumber()).isEqualTo(2);
        assertThat(next.status()).isEqualTo(Status.PENDING);
        assertThat(next.reviewReason()).isNull(); assertThat(next.reviewedAt()).isNull();
        assertThat(rejected.reviewReason()).isEqualTo("Correction needed");
    }
    @Test void approvedApplicationsAreImmutableAndDecidedOnlyOnce() {
        var approved=pending().decide(true,9L,"Welcome",now);
        assertThatThrownBy(() -> approved.edit(details)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> approved.decide(false,9L,"No",now)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> approved.resubmit(now)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> pending().resubmit(now)).isInstanceOf(BusinessRuleViolation.class);
    }
}
