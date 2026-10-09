package sg.nus.carelink.visit.controller.dto;

import java.time.LocalDateTime;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;

public record ElderVisitConfirmationResponse(
        Long id,
        Long visitId,
        Long elderId,
        ElderConfirmation.ConfirmationStatus confirmationStatus,
        Byte rating,
        String comment,
        LocalDateTime confirmedAt
) {

    public static ElderVisitConfirmationResponse from(
            ElderConfirmation confirmation) {

        return new ElderVisitConfirmationResponse(
                confirmation.id(),
                confirmation.visitId(),
                confirmation.elderId(),
                confirmation.confirmationStatus(),
                confirmation.rating(),
                confirmation.comment(),
                confirmation.confirmedAt()
        );
    }
}