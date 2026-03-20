package org.leoric.expensetracker.expensetracker.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.UserExpenseTrackerRoleRepository;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.handler.exceptions.NotAuthorizedForThisExpenseTrackerException;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.ADMIN;

@Service
@RequiredArgsConstructor
public class ExpenseTrackerAccessServiceImpl implements ExpenseTrackerAccessService {

	private final UserExpenseTrackerRoleRepository userExpenseTrackerRoleRepository;

	@Override
	public void assertHasRoleOnExpenseTracker(UUID expenseTrackerId, User user, String allowedRoles) {
		if (user.hasGlobalRole(ADMIN)) {
			return;
		}

		String[] rolesArray = allowedRoles.split(";");
		for (String role : rolesArray) {
			if (userExpenseTrackerRoleRepository.existsByUserIdAndExpenseTrackerIdAndRoleName(
					user.getId(), expenseTrackerId, role.trim())) {
				return;
			}
		}

		throw new NotAuthorizedForThisExpenseTrackerException();
	}
}