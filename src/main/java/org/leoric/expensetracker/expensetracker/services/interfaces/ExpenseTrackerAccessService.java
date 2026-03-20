package org.leoric.expensetracker.expensetracker.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ExpenseTrackerAccessService {

	void assertHasRoleOnExpenseTracker(UUID expenseTrackerId, User user, String allowedRoles);
}