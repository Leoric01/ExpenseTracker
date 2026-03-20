package org.leoric.expensetracker.expensetracker.dto;

import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;

import java.util.UUID;

public record ExpenseTrackerAccessRequestAuthorizationInfo(UUID expenseTrackerId,
                                                           ExpenseTrackerAccessRequestType expenseTrackerAccessRequestType) {
}