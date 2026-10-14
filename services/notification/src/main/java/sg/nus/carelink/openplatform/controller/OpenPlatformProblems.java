package sg.nus.carelink.openplatform.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import sg.nus.carelink.openplatform.application.PortalService;
import sg.nus.carelink.openplatform.domain.model.CredentialsAlreadyCollected;
import sg.nus.carelink.openplatform.domain.model.InvalidField;
import sg.nus.carelink.openplatform.domain.model.ProviderNotApproved;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The Open API's errors, for its two controllers only: problem details with the stable {@code code}
 * partners branch on (docs/api/open-platform.yaml, {@code Problem}). The CareLink app's own API keeps
 * the shared handler's answers; the partners' contract adds a code to every one of them and answers a
 * few rules with other statuses.
 */
@RestControllerAdvice(assignableTypes = { OpenApplicationsController.class, PortalController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
class OpenPlatformProblems {

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

	@ExceptionHandler(InvalidField.class)
	ProblemDetail onInvalidField(InvalidField ex) {
		return validationFailed(Map.of(ex.field(), ex.getMessage()));
	}

	@ExceptionHandler(ProviderNotApproved.class)
	ProblemDetail onNotApproved(ProviderNotApproved ex) {
		return problem(HttpStatus.FORBIDDEN, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(CredentialsAlreadyCollected.class)
	ProblemDetail onCollected(CredentialsAlreadyCollected ex) {
		return problem(HttpStatus.GONE, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(BusinessRuleViolation.class)
	ProblemDetail onBusinessRule(BusinessRuleViolation ex) {
		return problem(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
	}

	@ExceptionHandler(PortalService.DeliveryNotFound.class)
	ProblemDetail onDeliveryNotFound(PortalService.DeliveryNotFound ex) {
		return problem(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "No delivery with that ID was made to this provider.");
	}

	/** The portal user has no application: their user was made, the application not kept. */
	@ExceptionHandler(ResourceNotFound.class)
	ProblemDetail onNotFound(ResourceNotFound ex) {
		return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Nothing was found.");
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
