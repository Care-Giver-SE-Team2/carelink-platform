package sg.nus.carelink.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.AuditUnavailable;
import sg.nus.carelink.shared.error.ResourceNotFound;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts exceptions handled by Spring MVC into RFC 9457 ProblemDetail responses.
 * Security filters handle authentication and CSRF failures separately.
 *
 * <p>Controllers must not catch exceptions and assemble their own error bodies —
 * that is how an error format ends up differing from author to author. The domain
 * layer throws {@link BusinessRuleViolation}; translating it to HTTP happens here.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Reports unavailable access recording without exposing protected content or storage details.
	 *
	 * @return A retryable 503 problem response
	 * @author Wang Zhili
	 */
	@ExceptionHandler(AuditUnavailable.class)
	ProblemDetail onAuditUnavailable() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Access could not be recorded. Please retry later.");
		problem.setTitle("Access recording unavailable");
		return problem;
	}

	@ExceptionHandler(BusinessRuleViolation.class)
	ProblemDetail onBusinessRuleViolation(BusinessRuleViolation ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Operation not allowed by a business rule");
		problem.setProperty("code", ex.code());
		return problem;
	}

	@ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
	ProblemDetail onOptimisticConflict(org.springframework.dao.OptimisticLockingFailureException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The record changed concurrently. Refresh and retry the operation.");
		problem.setTitle("Concurrent update");
		problem.setProperty("code", "CONCURRENT_UPDATE");
		return problem;
	}

	@ExceptionHandler(ResourceNotFound.class)
	ProblemDetail onResourceNotFound(ResourceNotFound ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Resource not found");
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Invalid request");
		problem.setProperty("fields", fields);
		return problem;
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail onAccessDenied(AccessDeniedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Not permitted to access this resource");
		problem.setTitle("Insufficient permission");
		return problem;
	}

	/**
	 * Report path or query parameters that cannot be converted to the expected format.
	 *
	 * @param ex Parameter conversion failure
	 * @return A standard 400 problem response identifying the invalid parameter
	 *
	 * @author Wang Zhili
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail onParameterTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return invalidParameter(ex.getName());
	}

	/**
	 * Report path values converted to null while retaining server errors for missing route declarations.
	 *
	 * @param ex Missing path variable or failed conversion
	 * @return A 400 response for invalid input, or a 500 response for a route configuration error
	 *
	 * @author Wang Zhili
	 */
	@ExceptionHandler(MissingPathVariableException.class)
	ProblemDetail onMissingPathVariable(MissingPathVariableException ex) {
		return ex.isMissingAfterConversion() ? invalidParameter(ex.getVariableName()) : onUnexpected(ex);
	}

	/**
	 * Report a required query parameter the caller left out.
	 *
	 * <p>Without this the omission reaches the catch-all and comes back as 500, which tells
	 * the caller the server is broken when in fact the request was incomplete, and writes an
	 * ERROR with a stack trace into the log for something nobody needs to investigate.
	 *
	 * @param ex The parameter the handler method required
	 * @return A standard 400 problem response naming the missing parameter
	 *
	 * @author Wang Ziyu
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	ProblemDetail onMissingRequestParameter(MissingServletRequestParameterException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Request is missing a required parameter");
		problem.setTitle("Invalid request");
		problem.setProperty("fields", Map.of(ex.getParameterName(), "Required"));
		return problem;
	}

	private ProblemDetail invalidParameter(String name) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Request parameter has an invalid format");
		problem.setTitle("Invalid request");
		problem.setProperty("fields", Map.of(name, "Invalid value"));
		return problem;
	}

	/**
	 * Report unreadable request bodies without exposing parser or Java type details.
	 *
	 * @return A standard 400 problem response
	 *
	 * @author Wang Zhili
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail onUnreadableRequest() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Request body is missing or does not match the expected JSON format");
		problem.setTitle("Invalid request");
		return problem;
	}

	/** Preserves explicit HTTP validation failures instead of treating them as server errors. */
	@ExceptionHandler(ResponseStatusException.class)
	ProblemDetail onResponseStatus(ResponseStatusException ex) {
		return ex.getBody();
	}

	/** Catch-all. Details go to the log only, never back to the caller, to avoid leaking internals. */
	@ExceptionHandler(Exception.class)
	ProblemDetail onUnexpected(Exception ex) {
		log.error("Unexpected exception", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error, please retry later");
		problem.setTitle("Internal error");
		return problem;
	}
}
