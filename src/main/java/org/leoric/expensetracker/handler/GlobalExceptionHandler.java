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
		ex.getMostSpecificCause();
		String message = ex.getMostSpecificCause().getMessage();
		return build(INVALID_JSON, message);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
		Set<String> errors = new HashSet<>();
		ex.getBindingResult().getAllErrors()
				.forEach(error -> errors.add(error.getDefaultMessage()));

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
		return build(INSUFFICIENT_ROLE, ex.getMessage());
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ExceptionResponse> handleBadCredentialsException(BadCredentialsException ex) {
		return build(BAD_CREDENTIALS, ex.getMessage());
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ExceptionResponse> handleUsernameNotFoundException(UsernameNotFoundException ex) {
		return build(USERNAME_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(LockedException.class)
	public ResponseEntity<ExceptionResponse> handleLockedException(LockedException ex) {
		return build(ACCOUNT_LOCKED, ex.getMessage());
	}

	@ExceptionHandler(DisabledException.class)
	public ResponseEntity<ExceptionResponse> handleDisabledException(DisabledException ex) {
		return build(ACCOUNT_DISABLED, ex.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ExceptionResponse> handleAuthenticationException(AuthenticationException ex) {
		return build(BAD_CREDENTIALS, ex.getMessage());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ExceptionResponse> handleRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
		return build(HTTP_METHOD_NOT_ALLOWED, ex.getMessage());
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ExceptionResponse> handleNoHandlerFoundException(NoHandlerFoundException ex) {
		return build(ENDPOINT_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ExceptionResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
		return build(ENDPOINT_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ExceptionResponse> handleEntityNotFoundException(EntityNotFoundException ex) {
		return build(ENTITY_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(IncorrectCurrentPasswordException.class)
	public ResponseEntity<ExceptionResponse> handleIncorrectCurrentPasswordException(IncorrectCurrentPasswordException ex) {
		log.warn("Incorrect current password: {}", ex.getMessage());
		return build(INCORRECT_CURRENT_PASSWORD, ex.getMessage());
	}

	@ExceptionHandler(NewPasswordDoesNotMatchException.class)
	public ResponseEntity<ExceptionResponse> handleNewPasswordDoesNotMatchException(NewPasswordDoesNotMatchException ex) {
		log.warn("New password does not match: {}", ex.getMessage());
		return build(NEW_PASSWORD_DOES_NOT_MATCH, ex.getMessage());
	}

	@ExceptionHandler(EmailAlreadyInUseException.class)
	public ResponseEntity<ExceptionResponse> handleEmailAlreadyInUseException(EmailAlreadyInUseException ex) {
		log.warn("Email already in use: {}", ex.getMessage());
		return build(EMAIL_ALREADY_IN_USE, ex.getMessage());
	}

	@ExceptionHandler(NotAuthorizedForThisExpenseTrackerException.class)
	public ResponseEntity<ExceptionResponse> handleNotAuthorizedForThisExpenseTrackerException(NotAuthorizedForThisExpenseTrackerException ex) {
		return build(NOT_AUTHORIZED_FOR_THIS_EXPENSE_TRACKER, ex.getMessage());
	}

	@ExceptionHandler(DuplicateExpenseTrackerNameException.class)
	public ResponseEntity<ExceptionResponse> handleDuplicateExpenseTrackerNameException(DuplicateExpenseTrackerNameException ex) {
		return build(DUPLICATE_EXPENSE_TRACKER_NAME, ex.getMessage());
	}

	@ExceptionHandler(OperationNotPermittedException.class)
	public ResponseEntity<ExceptionResponse> handleOperationNotPermittedException(OperationNotPermittedException ex) {
		return build(OPERATION_NOT_PERMITTED, ex.getMessage());
	}

	@ExceptionHandler(InsufficientRoleException.class)
	public ResponseEntity<ExceptionResponse> handleInsufficientRoleException(InsufficientRoleException ex) {
		return build(INSUFFICIENT_ROLE, ex.getMessage());
	}

	@ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
	public ResponseEntity<ExceptionResponse> handleClientDisconnect(Exception ex) {
		log.debug("Client disconnected before response could be sent: {}", ex.getClass().getSimpleName());
		return null;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ExceptionResponse> handleGeneralException(Exception ex) {
		log.warn("General Exception exception - error is operation not covered in BusinessErrorCodes", ex);
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