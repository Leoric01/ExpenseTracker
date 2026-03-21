package org.leoric.expensetracker.handler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@Getter
public enum BusinessErrorCodes {

	BAD_CREDENTIALS("B-1001", UNAUTHORIZED, "Login and / or Password is incorrect"),
	USERNAME_NOT_FOUND("B-1002", UNAUTHORIZED, "Login and / or Password is incorrect"),
	ACCOUNT_DISABLED("B-1003", FORBIDDEN, "User account is disabled"),
	ACCOUNT_LOCKED("B-1004", FORBIDDEN, "User account is locked"),
	INSUFFICIENT_ROLE("B-1005", FORBIDDEN, "You do not have sufficient role to perform this operation"),

	INCORRECT_CURRENT_PASSWORD("B-1101", BAD_REQUEST, "Current password is incorrect"),
	NEW_PASSWORD_DOES_NOT_MATCH("B-1102", BAD_REQUEST, "The new password does not match"),
	EMAIL_ALREADY_IN_USE("B-1103", CONFLICT, "Email is already in use"),

	VALIDATION_FAILED("B-1201", BAD_REQUEST, "Validation failed"),
	INVALID_JSON("B-1202", BAD_REQUEST, "Invalid JSON"),
	OPERATION_NOT_PERMITTED("B-1203", BAD_REQUEST, "Operation is not permitted"),

	HTTP_METHOD_NOT_ALLOWED("B-1301", METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint"),
	ENDPOINT_NOT_FOUND("B-1302", NOT_FOUND, "Requested endpoint does not exist"),

	ENTITY_NOT_FOUND("B-1401", NOT_FOUND, "Requested entity was not found"),

	NOT_AUTHORIZED_FOR_THIS_EXPENSE_TRACKER("B-2001", FORBIDDEN, "You are not authorized to manage this expense tracker"),
	REQUEST_ALREADY_RESOLVED("B-2002", CONFLICT, "This access request has already been resolved"),
	USER_NOT_FOUND("B-2003", NOT_FOUND, "User was not found"),
	CANNOT_INVITE_SELF("B-2004", BAD_REQUEST, "You cannot invite yourself to your expense tracker"),
	EXPENSE_TRACKER_ACCESS_REQUEST_NOT_FOUND("B-2005", NOT_FOUND, "Expense tracker access request was not found"),
	EXPENSE_TRACKER_ACCESS_REQUEST_NOT_OWNED("B-2006", FORBIDDEN, "You are not allowed to manage this access request"),
	REQUEST_NOT_OWNED("B-2007", FORBIDDEN, "Request does not belong to user"),
	DUPLICATE_EXPENSE_TRACKER_NAME("B-2008", CONFLICT, "An expense tracker with this name already exists"),
	DUPLICATE_WALLET_NAME("B-3001", CONFLICT, "A wallet with this name already exists in this expense tracker"),
	DUPLICATE_CATEGORY_NAME("B-4001", CONFLICT, "A category with this name already exists at this level"),

	INTERNAL_ERROR("B-9999", INTERNAL_SERVER_ERROR, "Internal error, please contact the admin");

	private final String code;
	private final String description;
	private final HttpStatus httpStatus;

	BusinessErrorCodes(String code, HttpStatus httpStatus, String description) {
		this.code = code;
		this.description = description;
		this.httpStatus = httpStatus;
	}
}