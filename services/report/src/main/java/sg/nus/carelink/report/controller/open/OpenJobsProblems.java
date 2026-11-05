package sg.nus.carelink.report.controller.open;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import sg.nus.carelink.report.application.ProviderJobService;
import sg.nus.carelink.report.domain.model.ProviderJob;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The job API's errors: problem details with the stable {@code code} partners branch on
 * (docs/api/open-platform.yaml, {@code Problem}), for {@link OpenJobsController} only. The CareLink
 * app's own API keeps the shared handler's answers.
 */
@RestControllerAdvice(assignableTypes = OpenJobsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class OpenJobsProblems {

	@ExceptionHandler(ProviderJobService.UnknownClient.class)
	ProblemDetail onUnknownClient(ProviderJobService.UnknownClient ex, HttpServletResponse response) {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
		return problem(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "The access token's client is no longer valid.");
	}

	@ExceptionHandler(ProviderJobService.ProviderSuspended.class)
	ProblemDetail onSuspended(ProviderJobService.ProviderSuspended ex) {
		return problem(HttpStatus.FORBIDDEN, "PROVIDER_SUSPENDED", ex.getMessage());
	}

	@ExceptionHandler(ProviderJobService.JobNotFound.class)
	ProblemDetail onNotFound(ProviderJobService.JobNotFound ex) {
		return problem(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", ex.getMessage());
	}

	@ExceptionHandler(ProviderJobService.KeyReused.class)
	ProblemDetail onKeyReused(ProviderJobService.KeyReused ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", ex.getMessage());
	}

	@ExceptionHandler(OpenJobDtos.KeyMissing.class)
	ProblemDetail onKeyMissing(OpenJobDtos.KeyMissing ex) {
		return problem(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", ex.getMessage());
	}

	@ExceptionHandler(OpenJobDtos.KeyTooLong.class)
	ProblemDetail onKeyTooLong(OpenJobDtos.KeyTooLong ex) {
		return validationFailed(Map.of(OpenJobsController.IDEMPOTENCY_KEY, ex.getMessage()));
	}

	@ExceptionHandler(ProviderJob.InvalidTimes.class)
	ProblemDetail onInvalidTimes(ProviderJob.InvalidTimes ex) {
		return problem(HttpStatus.BAD_REQUEST, "INVALID_TIMES", ex.getMessage());
	}

	@ExceptionHandler(ProviderJob.MissingNote.class)
	ProblemDetail onMissingNote(ProviderJob.MissingNote ex) {
		return validationFailed(Map.of("note", ex.getMessage()));
	}

	/** JOB_CANCELLED, JOB_NOT_ACTIONABLE and RESPONSE_DEADLINE_PASSED. */
	@ExceptionHandler(BusinessRuleViolation.class)
	ProblemDetail onBusinessRule(BusinessRuleViolation ex) {
		return problem(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
	}

	/** Two answers to one job at once; the second, sent again, sees the first. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail onConcurrentUpdate(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "JOB_NOT_ACTIONABLE", "The job changed while it was answered; read it again.");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail onInvalidBody(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return validationFailed(fields);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ProblemDetail onInvalidParameter(HandlerMethodValidationException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getParameterValidationResults().forEach(result -> fields.putIfAbsent(
				result.getMethodParameter().getParameterName(),
				result.getResolvableErrors().getFirst().getDefaultMessage()));
		return validationFailed(fields);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail onConstraintViolation(ConstraintViolationException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getConstraintViolations().forEach(violation -> {
			String path = violation.getPropertyPath().toString();
			fields.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
		});
		return validationFailed(fields);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return validationFailed(Map.of(ex.getName(), "Invalid value"));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail onUnreadable(HttpMessageNotReadableException ex) {
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request body could not be read.");
	}

	private static ProblemDetail validationFailed(Map<String, String> fields) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
		problem.setProperty("fields", fields);
		return problem;
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code);
		return problem;
	}
}
