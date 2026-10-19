package sg.nus.carelink.profile.controller;

import org.springframework.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Keep upload errors in the shared RFC 9457 shape without changing other modules. */
@RestControllerAdvice(assignableTypes=CaregiverCredentialController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CredentialUploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail oversized() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Use at most 3 documents, each at most 5 MiB.");
    }
    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail missing() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Select the required certificate documents.");
    }
}
