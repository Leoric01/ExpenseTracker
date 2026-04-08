package org.leoric.expensetracker.expensetracker.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.Role;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.models.UserExpenseTrackerRole;
import org.leoric.expensetracker.auth.repositories.RoleRepository;
import org.leoric.expensetracker.auth.repositories.UserRepository;
import org.leoric.expensetracker.expensetracker.dto.CreateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMineResponseDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponseDto;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.mapstruct.ExpenseTrackerMapper;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.expensetracker.services.interfaces.ExpenseTrackerService;
import org.leoric.expensetracker.handler.exceptions.DuplicateExpenseTrackerNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.leoric.expensetracker.ExpenseTrackerApplication.EXPENSETRACKER_OWNER;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpenseTrackerServiceImpl implements ExpenseTrackerService {

	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final ExpenseTrackerMapper expenseTrackerMapper;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final AssetRepository assetRepository;

	@Override
	@Transactional
	public ExpenseTrackerResponseDto expenseTrackerCreate(User currentUser, CreateExpenseTrackerRequestDto request) {
		currentUser = userRepository.findById(currentUser.getId())
				.orElseThrow(() -> new EntityNotFoundException("User not found"));

		if (expenseTrackerRepository.existsByCreatedByOwnerIdAndNameIgnoreCase(currentUser.getId(), request.name())) {
			throw new DuplicateExpenseTrackerNameException(
					"Expense tracker with name '%s' already exists".formatted(request.name()));
		}

		Role ownerRole = roleRepository.findByName(EXPENSETRACKER_OWNER)
				.orElseThrow(() -> new EntityNotFoundException("Role " + EXPENSETRACKER_OWNER + " not found"));

		Asset displayAsset = null;
		if (request.preferredDisplayAssetId() != null) {
			displayAsset = assetRepository.findById(request.preferredDisplayAssetId())
					.orElseThrow(() -> new EntityNotFoundException("Asset not found"));
		}

		ExpenseTracker tracker = ExpenseTracker.builder()
				.name(request.name())
				.description(request.description())
				.preferredDisplayAsset(displayAsset)
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
	public ExpenseTrackerResponseDto expenseTrackerFindById(User currentUser, UUID id) {
		ExpenseTracker tracker = getTrackerOrThrow(id);
		return expenseTrackerMapper.toResponse(tracker);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerResponseDto> expenseTrackerFindAll(User currentUser, String search, Pageable pageable) {
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
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerMineResponseDto> expenseTrackerFindAllMine(User currentUser, String search, Pageable pageable) {
		Page<ExpenseTracker> page;
		if (search != null && !search.isBlank()) {
			page = expenseTrackerRepository
					.findByUsersIdAndActiveTrueAndNameContainingIgnoreCase(currentUser.getId(), search, pageable);
		} else {
			page = expenseTrackerRepository
					.findByUsersIdAndActiveTrue(currentUser.getId(), pageable);
		}
		return page.map(tracker -> {
			String role = tracker.getUserExpenseTrackerRoles().stream()
					.filter(r -> r.getUser().getId().equals(currentUser.getId()))
					.map(r -> r.getRole().getName())
					.findFirst()
					.orElse(null);
			return expenseTrackerMapper.toMineResponse(tracker, role);
		});
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ExpenseTrackerResponseDto> expenseTrackerFindAllButMine(User currentUser, String search, Pageable pageable) {
		Page<ExpenseTracker> page;
		if (search != null && !search.isBlank()) {
			page = expenseTrackerRepository.findAllNotMineWithSearch(currentUser.getId(), search, pageable);
		} else {
			page = expenseTrackerRepository.findAllNotMine(currentUser.getId(), pageable);
		}
		return page.map(expenseTrackerMapper::toResponse);
	}

	@Override
	@Transactional
	public ExpenseTrackerResponseDto expenseTrackerUpdate(User currentUser, UUID id, UpdateExpenseTrackerRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(id);

		expenseTrackerMapper.updateFromDto(request, tracker);

		if (request.preferredDisplayAssetId() != null) {
			Asset displayAsset = assetRepository.findById(request.preferredDisplayAssetId())
					.orElseThrow(() -> new EntityNotFoundException("Asset not found"));
			tracker.setPreferredDisplayAsset(displayAsset);
		}

		tracker = expenseTrackerRepository.save(tracker);

		log.info("User {} updated expense tracker '{}'", currentUser.getEmail(), tracker.getName());
		return expenseTrackerMapper.toResponse(tracker);
	}

	@Override
	@Transactional
	public void expenseTrackerDeactivate(User currentUser, UUID id) {
		ExpenseTracker tracker = getTrackerOrThrow(id);

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