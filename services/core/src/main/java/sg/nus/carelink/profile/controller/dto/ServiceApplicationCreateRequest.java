package sg.nus.carelink.profile.controller.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

/** The browser supplies the selected elder and requested services; the server owns the snapshot. */
@JsonDeserialize(using = ServiceApplicationCreateRequestDeserializer.class)
public record ServiceApplicationCreateRequest(@NotNull @Positive Long elderId,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @CodePointLength(max = 100) String> careNeeds,
        @CodePointLength(max = 2000) String notes) {
}
