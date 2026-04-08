package org.leoric.expensetracker.holding.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.account.repositories.AccountRepository;
import org.leoric.expensetracker.asset.models.Asset;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.holding.dto.CreateHoldingRequestDto;
import org.leoric.expensetracker.holding.dto.HoldingResponseDto;
import org.leoric.expensetracker.holding.dto.HoldingSummaryResponseDto;
import org.leoric.expensetracker.holding.mapstruct.HoldingMapper;
import org.leoric.expensetracker.holding.models.Holding;
import org.leoric.expensetracker.holding.repositories.HoldingRepository;
import org.leoric.expensetracker.holding.services.interfaces.HoldingService;
import org.leoric.expensetracker.holding.services.interfaces.HoldingSummaryBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class HoldingServiceImpl implements HoldingService {

	private final HoldingRepository holdingRepository;
	private final AccountRepository accountRepository;
	private final AssetRepository assetRepository;
	private final HoldingSummaryBuilder holdingSummaryBuilder;
	private final HoldingMapper holdingMapper;

	@Override
	@Transactional
	public HoldingResponseDto holdingCreate(User currentUser, UUID trackerId, CreateHoldingRequestDto request) {
		Account account = getAccountOrThrow(request.accountId());
		assertAccountBelongsToTracker(account, trackerId);

		Asset asset = assetRepository.findById(request.assetId())
				.orElseThrow(() -> new EntityNotFoundException("Asset not found"));

		if (holdingRepository.existsByAccountIdAndAssetId(account.getId(), asset.getId())) {
			throw new OperationNotPermittedException(
					"Holding for asset '%s' already exists in account '%s'".formatted(asset.getCode(), account.getName()));
		}

		long initialAmount = request.initialAmount() != null ? request.initialAmount() : 0L;

		Holding holding = Holding.builder()
				.account(account)
				.asset(asset)
				.initialAmount(initialAmount)
				.currentAmount(initialAmount)
				.build();

		holding = holdingRepository.save(holding);
		log.info("User {} created holding '{}/{}' in account '{}' (tracker '{}')",
				currentUser.getEmail(), asset.getCode(), account.getName(),
				account.getInstitution().getName(), account.getInstitution().getExpenseTracker().getName());
		return holdingMapper.toResponse(holding);
	}

	@Override
	@Transactional(readOnly = true)
	public HoldingResponseDto holdingFindById(User currentUser, UUID trackerId, UUID holdingId) {
		Holding holding = getHoldingOrThrow(holdingId);
		assertHoldingBelongsToTracker(holding, trackerId);
		return holdingMapper.toResponse(holding);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<HoldingResponseDto> holdingFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return holdingRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(holdingMapper::toResponse);
		}
		return holdingRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(holdingMapper::toResponse);
	}

	@Override
	@Transactional
	public void holdingDeactivate(User currentUser, UUID trackerId, UUID holdingId) {
		Holding holding = getHoldingOrThrow(holdingId);
		assertHoldingBelongsToTracker(holding, trackerId);

		if (!holding.isActive()) {
			throw new OperationNotPermittedException("Holding is already deactivated");
		}

		holding.setActive(false);
		holdingRepository.save(holding);
		log.info("User {} deactivated holding '{}/{}' in tracker '{}'",
				currentUser.getEmail(), holding.getAsset().getCode(), holding.getAccount().getName(),
				holding.getAccount().getInstitution().getExpenseTracker().getName());
	}

	@Override
	@Transactional(readOnly = true)
	public HoldingSummaryResponseDto holdingSummary(User currentUser, UUID trackerId, UUID holdingId, Instant from, Instant to) {
		Holding holding = getHoldingOrThrow(holdingId);
		assertHoldingBelongsToTracker(holding, trackerId);
		return holdingSummaryBuilder.buildSummary(holding, from, to);
	}

	private Account getAccountOrThrow(UUID accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new EntityNotFoundException("Account not found"));
	}

	private Holding getHoldingOrThrow(UUID holdingId) {
		return holdingRepository.findById(holdingId)
				.orElseThrow(() -> new EntityNotFoundException("Holding not found"));
	}

	private void assertAccountBelongsToTracker(Account account, UUID trackerId) {
		if (!account.getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Account not found in this expense tracker");
		}
	}

	private void assertHoldingBelongsToTracker(Holding holding, UUID trackerId) {
		if (!holding.getAccount().getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Holding not found in this expense tracker");
		}
	}
}