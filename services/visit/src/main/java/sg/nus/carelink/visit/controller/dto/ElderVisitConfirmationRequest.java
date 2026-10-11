package sg.nus.carelink.visit.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;

public record ElderVisitConfirmationRequest(

        @NotNull
        ElderConfirmation.ConfirmationStatus confirmationStatus,

        @Min(1)
        @Max(5)
        Byte rating,

        @Size(max = 2000)
        String comment
) {
}