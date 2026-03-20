package org.leoric.expensetracker.handler;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.leoric.expensetracker.handler.exceptions.EmailAlreadyInUseException;
import org.leoric.expensetracker.handler.exceptions.DuplicateExpenseTrackerNameException;
import org.leoric.expensetracker.handler.exceptions.IncorrectCurrentPasswordException;
import org.leoric.expensetracker.handler.exceptions.InsufficientRoleException;
import org.leoric.expensetracker.handler.exceptions.NewPasswordDoesNotMatchException;
import org.leoric.expensetracker.handler.exceptions.NotAuthorizedForThisExpenseTrackerException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashSet;
import java.util.Set;

import static org.leoric.expensetracker.handler.BusinessErrorCodes.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ExceptionResponse> handleMessageNotReadableException(HttpMessageNotReadableException ex) {
		log.warn("[{}] {}", INVALID_JSON.getCode(), ex.getMostSpecificCause().getMessage(), ex);
		return build(INVALID_JSON, ex.getMostSpecificCause().getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
		Set<String> errors = new HashSet<>();
		ex.getBindingResult().getAllErrors()
				.forEach(error -> errors.add(error.getDefaultMessage()));

		log.warn("[{}] Validation failed: {}", VALIDATION_FAILED.getCode(), errors, ex);

		return ResponseEntity
				.status(VALIDATION_FAILED.getHttpStatus())
				.body(ExceptionResponse.builder()
						      .businessErrorCode(VALIDATION_FAILED.getCode())
						      .businessErrorDescription(VALIDATION_FAILED.getDescription())
						      .error(ex.getMessage())
						      .validationErrors(errors)
						      .build());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ExceptionResponse> handleAccessDeniedException(AccessDeniedException ex) {
		log.warn("[{}] {}", INSUFFICIENT_ROLE.getCode(), ex.getMessage(), ex);
		return build(INSUFFICIENT_ROLE, ex.getMessage());
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ExceptionResponse> handleBadCredentialsException(BadCredentialsException ex) {
		log.warn("[{}] {}", BAD_CREDENTIALS.getCode(), ex.getMessage(), ex);
		return build(BAD_CREDENTIALS, ex.getMessage());
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ExceptionResponse> handleUsernameNotFoundException(UsernameNotFoundException ex) {
		log.warn("[{}] {}", USERNAME_NOT_FOUND.getCode(), ex.getMessage(), ex);
		return build(USERNAME_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(LockedException.class)
	public ResponseEntity<ExceptionResponse> handleLockedException(LockedException ex) {
		log.warn("[{}] {}", ACCOUNT_LOCKED.getCode(), ex.getMessage(), ex);
		return build(ACCOUNT_LOCKED, ex.getMessage());
	}

	@ExceptionHandler(DisabledException.class)
	public ResponseEntity<ExceptionResponse> handleDisabledException(DisabledException ex) {
		log.warn("[{}] {}", ACCOUNT_DISABLED.getCode(), ex.getMessage(), ex);
		return build(ACCOUNT_DISABLED, ex.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ExceptionResponse> handleAuthenticationException(AuthenticationException ex) {
		log.warn("[{}] {}", BAD_CREDENTIALS.getCode(), ex.getMessage(), ex);
		return build(BAD_CREDENTIALS, ex.getMessage());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ExceptionResponse> handleRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
		log.warn("[{}] {}", HTTP_METHOD_NOT_ALLOWED.getCode(), ex.getMessage(), ex);
		return build(HTTP_METHOD_NOT_ALLOWED, ex.getMessage());
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ExceptionResponse> handleNoHandlerFoundException(NoHandlerFoundException ex) {
		log.warn("[{}] {}", ENDPOINT_NOT_FOUND.getCode(), ex.getMessage(), ex);
		return build(ENDPOINT_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ExceptionResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
		log.warn("[{}] {}", ENDPOINT_NOT_FOUND.getCode(), ex.getMessage(), ex);
		return build(ENDPOINT_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ExceptionResponse> handleEntityNotFoundException(EntityNotFoundException ex) {
		log.warn("[{}] {}", ENTITY_NOT_FOUND.getCode(), ex.getMessage(), ex);
		return build(ENTITY_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(IncorrectCurrentPasswordException.class)
	public ResponseEntity<ExceptionResponse> handleIncorrectCurrentPasswordException(IncorrectCurrentPasswordException ex) {
		log.warn("[{}] {}", INCORRECT_CURRENT_PASSWORD.getCode(), ex.getMessage(), ex);
		return build(INCORRECT_CURRENT_PASSWORD, ex.getMessage());
	}

	@ExceptionHandler(NewPasswordDoesNotMatchException.class)
	public ResponseEntity<ExceptionResponse> handleNewPasswordDoesNotMatchException(NewPasswordDoesNotMatchException ex) {
		log.warn("[{}] {}", NEW_PASSWORD_DOES_NOT_MATCH.getCode(), ex.getMessage(), ex);
		return build(NEW_PASSWORD_DOES_NOT_MATCH, ex.getMessage());
	}

	@ExceptionHandler(EmailAlreadyInUseException.class)
	public ResponseEntity<ExceptionResponse> handleEmailAlreadyInUseException(EmailAlreadyInUseException ex) {
		log.warn("[{}] {}", EMAIL_ALREADY_IN_USE.getCode(), ex.getMessage(), ex);
		return build(EMAIL_ALREADY_IN_USE, ex.getMessage());
	}

	@ExceptionHandler(NotAuthorizedForThisExpenseTrackerException.class)
	public ResponseEntity<ExceptionResponse> handleNotAuthorizedForThisExpenseTrackerException(NotAuthorizedForThisExpenseTrackerException ex) {
		log.warn("[{}] {}", NOT_AUTHORIZED_FOR_THIS_EXPENSE_TRACKER.getCode(), ex.getMessage(), ex);
		return build(NOT_AUTHORIZED_FOR_THIS_EXPENSE_TRACKER, ex.getMessage());
	}

	@ExceptionHandler(DuplicateExpenseTrackerNameException.class)
	public ResponseEntity<ExceptionResponse> handleDuplicateExpenseTrackerNameException(DuplicateExpenseTrackerNameException ex) {
		log.warn("[{}] {}", DUPLICATE_EXPENSE_TRACKER_NAME.getCode(), ex.getMessage(), ex);
		return build(DUPLICATE_EXPENSE_TRACKER_NAME, ex.getMessage());
	}

	@ExceptionHandler(OperationNotPermittedException.class)
	public ResponseEntity<ExceptionResponse> handleOperationNotPermittedException(OperationNotPermittedException ex) {
		log.warn("[{}] {}", OPERATION_NOT_PERMITTED.getCode(), ex.getMessage(), ex);
		return build(OPERATION_NOT_PERMITTED, ex.getMessage());
	}

	@ExceptionHandler(InsufficientRoleException.class)
	public ResponseEntity<ExceptionResponse> handleInsufficientRoleException(InsufficientRoleException ex) {
		log.warn("[{}] {}", INSUFFICIENT_ROLE.getCode(), ex.getMessage(), ex);
		return build(INSUFFICIENT_ROLE, ex.getMessage());
	}

	@ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
	public ResponseEntity<ExceptionResponse> handleClientDisconnect(Exception ex) {
		log.debug("Client disconnected: {}", ex.getClass().getSimpleName());
		return null;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ExceptionResponse> handleGeneralException(Exception ex) {
		log.error("[{}] Unhandled exception", INTERNAL_ERROR.getCode(), ex);
		return build(INTERNAL_ERROR, ex.getMessage());
	}

	private ResponseEntity<ExceptionResponse> build(BusinessErrorCodes code, String errorMessage) {
		return ResponseEntity
				.status(code.getHttpStatus())
				.body(ExceptionResponse.builder()
						      .businessErrorCode(code.getCode())
						      .businessErrorDescription(code.getDescription())
						      .error(errorMessage)
						      .build());
	}
}