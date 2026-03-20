package org.leoric.expensetracker.expensetracker.dto;

import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseTrackerAccessRequestResponse(
		UUID id,
		UUID expenseTrackerId,
		String expenseTrackerName,
		UUID userId,
		String userFullName,
		String userEmail,
		ExpenseTrackerAccessRequestStatus status,
		ExpenseTrackerAccessRequestType type,
		OffsetDateTime requestDate,
		OffsetDateTime approvalDate,
		String approvedByFullName,
		String invitedByFullName
) {
}