package org.leoric.expensetracker.expensetracker.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequest;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponse;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequest;
import org.leoric.expensetracker.expensetracker.mapstruct.ExpenseTrackerMapper;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerAccessService;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerService;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_MEMBER;
import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpenseTrackerServiceImpl implements ExpenseTrackerService {

	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ExpenseTrackerMapper expenseTrackerMapper;
	private final ExpenseTrackerAccessService expenseTrackerAccessService;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public ExpenseTrackerResponse expenseTrackerCreate(User currentUser, CreateExpenseTrackerRequest request) {
		currentUser = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		Role ownerRole = roleRepository.findByName(EXPENSETRACKER_OWNER)
				.orElseThrow(() -> new EntityNotFoundException("Role " + EXPENSETRACKER_OWNER + " not found"));

		ExpenseTracker tracker = ExpenseTracker.builder()
				.name(request.name())
				.description(request.description())
				.defaultCurrencyCode(request.defaultCurrencyCode().toUpperCase())
				.createdByOwner(currentUser)
				.build();

		currentUser.getExpenseTrackers().add(tracker);
		tracker.getUsers().add(currentUser);

		UserExpenseTrackerRole ownerRoleAssignment = UserExpenseTrackerRole.builder()
				.user(currentUser)
				.expenseTracker(tracker)
				.role(ownerRole)
				.build();

		tracker.getUserExpenseTrackerRoles().add(ownerRoleAssignment);

		tracker = expenseTrackerRepository.save(tracker);
		log.info("User {} created expense tracker '{}'", currentUser.getEmail(), tracker.getName());

		return expenseTrackerMapper.toResponse(tracker);
	}

	@Override
	@Transactional(readOnly = true)
	public ExpenseTrackerResponse expenseTrackerFindById(User currentUser, UUID id) {
		ExpenseTracker tracker = getTrackerOrThrow(id);
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(id, currentUser, EXPENSETRACKER_OWNER + ";" + EXPENSETRACKER_MEMBER);
		return expenseTrackerMapper.toResponse(tracker);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerResponse> expenseTrackerFindAll(User currentUser, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return expenseTrackerRepository
					.findByUsersIdAndActiveTrueAndNameContainingIgnoreCase(currentUser.getId(), search, pageable)
					.map(expenseTrackerMapper::toResponse);
		}
		return expenseTrackerRepository
				.findByUsersIdAndActiveTrue(currentUser.getId(), pageable)
				.map(expenseTrackerMapper::toResponse);
	}

	@Override
	@Transactional
	public ExpenseTrackerResponse expenseTrackerUpdate(User currentUser, UUID id, UpdateExpenseTrackerRequest request) {
		ExpenseTracker tracker = getTrackerOrThrow(id);
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(id, currentUser, EXPENSETRACKER_OWNER);

		expenseTrackerMapper.updateFromDto(request, tracker);
		tracker = expenseTrackerRepository.save(tracker);

		log.info("User {} updated expense tracker '{}'", currentUser.getEmail(), tracker.getName());
		return expenseTrackerMapper.toResponse(tracker);
	}

	@Override
	@Transactional
	public void expenseTrackerDeactivate(User currentUser, UUID id) {
		ExpenseTracker tracker = getTrackerOrThrow(id);
		expenseTrackerAccessService.assertHasRoleOnExpenseTracker(id, currentUser, EXPENSETRACKER_OWNER);

		if (!tracker.isActive()) {
			throw new OperationNotPermittedException("Expense tracker is already deactivated");
		}

		tracker.setActive(false);
		expenseTrackerRepository.save(tracker);
		log.info("User {} deactivated expense tracker '{}'", currentUser.getEmail(), tracker.getName());
	}

	private ExpenseTracker getTrackerOrThrow(UUID id) {
		return expenseTrackerRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}
}