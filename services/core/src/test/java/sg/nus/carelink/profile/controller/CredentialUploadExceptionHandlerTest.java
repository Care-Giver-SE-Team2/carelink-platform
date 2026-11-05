package sg.nus.carelink.profile.controller;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CredentialUploadExceptionHandlerTest {
    @Test void uploadErrorsHaveStablePrivateFriendlyProblemDetails() {
        var handler = new CredentialUploadExceptionHandler();
        assertThat(handler.oversized().getStatus()).isEqualTo(413);
        assertThat(handler.oversized().getDetail()).contains("5 MiB");
        assertThat(handler.missing().getStatus()).isEqualTo(400);
        assertThat(handler.missing().getDetail()).contains("documents");
    }
}
