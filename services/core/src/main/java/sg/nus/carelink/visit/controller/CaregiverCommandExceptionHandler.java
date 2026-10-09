package sg.nus.carelink.visit.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.nus.carelink.visit.application.CaregiverCommandUnavailable;

/** Scoped to these command endpoints; does not alter other modules' error contracts. */
@Order(-1)
@RestControllerAdvice(assignableTypes={CaregiverIncidentController.class,CaregiverVisitExecutionController.class,CaregiverHealthController.class})
class CaregiverCommandExceptionHandler {
    @ExceptionHandler(CaregiverCommandUnavailable.class)
    ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"The result could not be confirmed. Check saved results before retrying the same command.");
    }
}
