package org.leoric.expensetracker.wallet.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.DuplicateWalletNameException;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.wallet.dto.CreateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.UpdateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.WalletResponseDto;
import org.leoric.expensetracker.wallet.mapstruct.WalletMapper;
import org.leoric.expensetracker.wallet.models.Wallet;
import org.leoric.expensetracker.wallet.repositories.WalletRepository;
import org.leoric.expensetracker.wallet.services.interfaces.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

	private final WalletRepository walletRepository;
	private final ExpenseTrackerRepository expenseTrackerRepository;
	private final WalletMapper walletMapper;
	private final ImageService imageService;

	@Override
	@Transactional
	public WalletResponseDto walletCreate(User currentUser, UUID trackerId, CreateWalletRequestDto request) {
		ExpenseTracker tracker = getTrackerOrThrow(trackerId);

		if (walletRepository.existsByExpenseTrackerIdAndNameIgnoreCase(trackerId, request.name())) {
			throw new DuplicateWalletNameException(
					"Wallet with name '%s' already exists in this expense tracker".formatted(request.name()));
		}

		long initialBalance = request.initialBalance() != null ? request.initialBalance() : 0L;

		Wallet wallet = Wallet.builder()
				.expenseTracker(tracker)
				.name(request.name())
				.walletType(request.walletType())
				.currencyCode(request.currencyCode().toUpperCase())
				.initialBalance(initialBalance)
				.currentBalance(initialBalance)
				.build();

		wallet = walletRepository.save(wallet);
		log.info("User {} created wallet '{}' in tracker '{}'", currentUser.getEmail(), wallet.getName(), tracker.getName());

		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional(readOnly = true)
	public WalletResponseDto walletFindById(User currentUser, UUID trackerId, UUID walletId) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<WalletResponseDto> walletFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return walletRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(walletMapper::toResponse);
		}
		return walletRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(walletMapper::toResponse);
	}

	@Override
	@Transactional
	public WalletResponseDto walletUpdate(User currentUser, UUID trackerId, UUID walletId, UpdateWalletRequestDto request) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		walletMapper.updateFromDto(request, wallet);
		wallet = walletRepository.save(wallet);

		log.info("User {} updated wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional
	public void walletDeactivate(User currentUser, UUID trackerId, UUID walletId) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		if (!wallet.isActive()) {
			throw new OperationNotPermittedException("Wallet is already deactivated");
		}

		wallet.setActive(false);
		walletRepository.save(wallet);
		log.info("User {} deactivated wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public WalletResponseDto walletUploadIcon(User currentUser, UUID trackerId, UUID walletId, MultipartFile icon, String iconColor) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		String iconUrl = imageService.uploadImage(icon, "expense-tracker/wallets");
		wallet.setIconUrl(iconUrl);
		wallet.setIconColor(iconColor);
		wallet = walletRepository.save(wallet);

		log.info("User {} uploaded icon for wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
		return walletMapper.toResponse(wallet);
	}

	@Override
	@Transactional
	public WalletResponseDto walletDeleteIcon(User currentUser, UUID trackerId, UUID walletId) {
		Wallet wallet = getWalletOrThrow(walletId);
		assertWalletBelongsToTracker(wallet, trackerId);

		wallet.setIconUrl(null);
		wallet.setIconColor(null);
		wallet = walletRepository.save(wallet);

		log.info("User {} deleted icon for wallet '{}' in tracker '{}'",
				currentUser.getEmail(), wallet.getName(), wallet.getExpenseTracker().getName());
		return walletMapper.toResponse(wallet);
	}

	private ExpenseTracker getTrackerOrThrow(UUID trackerId) {
		return expenseTrackerRepository.findById(trackerId)
				.orElseThrow(() -> new EntityNotFoundException("Expense tracker not found"));
	}

	private Wallet getWalletOrThrow(UUID walletId) {
		return walletRepository.findById(walletId)
				.orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
	}

	private void assertWalletBelongsToTracker(Wallet wallet, UUID trackerId) {
		if (!wallet.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Wallet not found in this expense tracker");
		}
	}
}