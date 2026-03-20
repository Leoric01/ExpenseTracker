package org.leoric.expensetracker.auth.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.dto.UserInfoResponseDto;
import org.leoric.expensetracker.auth.dto.UserPasswordChangeDto;
import org.leoric.expensetracker.auth.dto.UserProfileUpdateDto;
import org.leoric.expensetracker.auth.dto.UserResponseFullDto;
import org.leoric.expensetracker.auth.mapstruct.UserMapper;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.OneTimePasswordTokenRepository;
import org.leoric.expensetracker.auth.repositories.UserExpenseTrackerRoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.auth.services.interfaces.UserService;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerAccessRequestRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.IncorrectCurrentPasswordException;
import org.leoric.expensetracker.handler.exceptions.NewPasswordDoesNotMatchException;
import org.leoric.expensetracker.recurring.repositories.RecurringBudgetTemplateRepository;
import org.leoric.expensetracker.recurring.repositories.RecurringTransactionTemplateRepository;
import org.leoric.expensetracker.transaction.repositories.TransactionRepository;
import org.leoric.expensetracker.wallet.repositories.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserMapper userMapper;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ExpenseTrackerAccessRequestRepository accessRequestRepository;
	private final UserExpenseTrackerRoleRepository userExpenseTrackerRoleRepository;
	private final OneTimePasswordTokenRepository otpTokenRepository;
	private final TransactionRepository transactionRepository;
	private final WalletRepository walletRepository;
	private final CategoryRepository categoryRepository;
	private final BudgetPlanRepository budgetPlanRepository;
	private final RecurringBudgetTemplateRepository recurringBudgetTemplateRepository;
	private final RecurringTransactionTemplateRepository recurringTransactionTemplateRepository;

	@Override
	@Transactional(readOnly = true)
    public UserInfoResponseDto profileMe(User currentUser) {
		currentUser = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new RuntimeException("User not found"));

		return userMapper.userToUserInfoResponseDto(currentUser);
	}

	@Override
	@Transactional
    public UserResponseFullDto profileUpdate(User currentUser, UserProfileUpdateDto dto) {
		log.debug("profileUpdate called for user {} with dto: firstName='{}', lastName='{}'",
				currentUser.getId(), dto.firstName(), dto.lastName());

		User user = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		log.debug("User before update: firstName='{}', lastName='{}'", user.getFirstName(), user.getLastName());

		userMapper.updateUserFromDto(dto, user);

		log.debug("User after mapper: firstName='{}', lastName='{}'", user.getFirstName(), user.getLastName());

		userRepository.save(user);

		return userMapper.userToUserResponseFull(user);
	}

	@Override
	@Transactional
    public void profileChangePassword(User currentUser, UserPasswordChangeDto dto) {
		User user = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		if (!passwordEncoder.matches(dto.oldPassword(), user.getPassword())) {
			throw new IncorrectCurrentPasswordException("Current password is incorrect");
		}

		if (!dto.newPassword().equals(dto.newConfirmationPassword())) {
			throw new NewPasswordDoesNotMatchException("New password and confirmation do not match");
		}

		user.setPassword(passwordEncoder.encode(dto.newPassword()));
		userRepository.save(user);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<UserResponseFullDto> profileFindAllPageable(String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return userRepository.findByEmailContainingIgnoreCase(search, pageable)
					.map(userMapper::userToUserResponseFull);
		}
		return userRepository.findAll(pageable)
				.map(userMapper::userToUserResponseFull);
	}

	@Override
	@Transactional
	public void profileDeleteMe(User currentUser) {
		User user = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		UUID userId = user.getId();

		// 1) Delete all expense trackers OWNED by this user (+ their sub-entities)
		List<ExpenseTracker> ownedTrackers = expenseTrackerRepository.findByCreatedByOwnerId(userId);
		for (ExpenseTracker tracker : ownedTrackers) {
			deleteExpenseTrackerCascade(tracker.getId());
		}

		// 2) Nullify references in access requests where user was approver or inviter
		accessRequestRepository.nullifyApprovedByUserId(userId);
		accessRequestRepository.nullifyInvitedByUserId(userId);

		// 3) Delete access requests where user is the requestor/invitee (on non-owned trackers)
		accessRequestRepository.deleteByUserId(userId);

		// 4) Remove user's role assignments on trackers they are member of (not owner)
		userExpenseTrackerRoleRepository.deleteByUserId(userId);

		// 5) Delete OTP tokens
		otpTokenRepository.deleteByUserId(userId);

		// 6) Clear ManyToMany join tables (roles, expenseTrackers) and cascaded collections
		user.getRoles().clear();
		user.getExpenseTrackers().clear();
		user.getUserExpenseTrackerRoles().clear();
		user.getNavbarFavourites().clear();

		// 7) Delete the user
		userRepository.delete(user);

		log.info("User {} ({}) has been permanently deleted", user.getEmail(), userId);
	}

	/**
	 * Deletes an expense tracker and ALL its sub-entities:
	 * transactions, wallets, categories, budget plans,
	 * recurring templates, access requests, role assignments.
	 */
	private void deleteExpenseTrackerCascade(UUID trackerId) {
		transactionRepository.deleteByExpenseTrackerId(trackerId);
		recurringTransactionTemplateRepository.deleteByExpenseTrackerId(trackerId);
		recurringBudgetTemplateRepository.deleteByExpenseTrackerId(trackerId);
		budgetPlanRepository.deleteByExpenseTrackerId(trackerId);
		walletRepository.deleteByExpenseTrackerId(trackerId);
		categoryRepository.deleteByExpenseTrackerId(trackerId);
		accessRequestRepository.deleteByExpenseTrackerId(trackerId);
		userExpenseTrackerRoleRepository.deleteByExpenseTrackerId(trackerId);

		// Remove all users from the join table before deleting the tracker
		ExpenseTracker tracker = expenseTrackerRepository.findById(trackerId).orElse(null);
		if (tracker != null) {
			tracker.getUsers().forEach(u -> u.getExpenseTrackers().remove(tracker));
			tracker.getUsers().clear();
			expenseTrackerRepository.delete(tracker);
		}
	}
}