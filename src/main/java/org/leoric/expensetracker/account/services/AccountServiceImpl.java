package org.leoric.expensetracker.account.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.leoric.expensetracker.account.dto.AccountResponseDto;
import org.leoric.expensetracker.account.dto.CreateAccountRequestDto;
import org.leoric.expensetracker.account.dto.UpdateAccountRequestDto;
import org.leoric.expensetracker.account.mapstruct.AccountMapper;
import org.leoric.expensetracker.account.models.Account;
import org.leoric.expensetracker.account.repositories.AccountRepository;
import org.leoric.expensetracker.account.services.interfaces.AccountService;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.institution.models.Institution;
import org.leoric.expensetracker.institution.repositories.InstitutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

	private final AccountRepository accountRepository;
	private final InstitutionRepository institutionRepository;
	private final AccountMapper accountMapper;
	private final ImageService imageService;

	@Override
	@Transactional
	public AccountResponseDto accountCreate(User currentUser, UUID trackerId, CreateAccountRequestDto request) {
		Institution institution = getInstitutionOrThrow(request.institutionId());
		assertInstitutionBelongsToTracker(institution, trackerId);

		if (accountRepository.existsByInstitutionIdAndNameIgnoreCase(institution.getId(), request.name())) {
			throw new OperationNotPermittedException(
					"Account with name '%s' already exists in institution '%s'".formatted(request.name(), institution.getName()));
		}

		Account account = Account.builder()
				.institution(institution)
				.name(request.name())
				.accountType(request.accountType())
				.description(request.description())
				.externalRef(request.externalRef())
				.build();

		account = accountRepository.save(account);
		log.info("User {} created account '{}' in institution '{}' (tracker '{}')",
				currentUser.getEmail(), account.getName(), institution.getName(), institution.getExpenseTracker().getName());
		return accountMapper.toResponse(account);
	}

	@Override
	@Transactional(readOnly = true)
	public AccountResponseDto accountFindById(User currentUser, UUID trackerId, UUID accountId) {
		Account account = getAccountOrThrow(accountId);
		assertAccountBelongsToTracker(account, trackerId);
		return accountMapper.toResponse(account);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<AccountResponseDto> accountFindAll(User currentUser, UUID trackerId, String search, Pageable pageable) {
		if (search != null && !search.isBlank()) {
			return accountRepository.findByExpenseTrackerIdAndActiveTrueWithSearch(trackerId, search, pageable)
					.map(accountMapper::toResponse);
		}
		return accountRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, pageable)
				.map(accountMapper::toResponse);
	}

	@Override
	@Transactional
	public AccountResponseDto accountUpdate(User currentUser, UUID trackerId, UUID accountId, UpdateAccountRequestDto request) {
		Account account = getAccountOrThrow(accountId);
		assertAccountBelongsToTracker(account, trackerId);

		accountMapper.updateFromDto(request, account);
		account = accountRepository.save(account);

		log.info("User {} updated account '{}' in tracker '{}'",
				currentUser.getEmail(), account.getName(), account.getInstitution().getExpenseTracker().getName());
		return accountMapper.toResponse(account);
	}

	@Override
	@Transactional
	public void accountDeactivate(User currentUser, UUID trackerId, UUID accountId) {
		Account account = getAccountOrThrow(accountId);
		assertAccountBelongsToTracker(account, trackerId);

		if (!account.isActive()) {
			throw new OperationNotPermittedException("Account is already deactivated");
		}

		account.setActive(false);
		accountRepository.save(account);
		log.info("User {} deactivated account '{}' in tracker '{}'",
				currentUser.getEmail(), account.getName(), account.getInstitution().getExpenseTracker().getName());
	}

	@Override
	@Transactional
	public AccountResponseDto accountUploadIcon(User currentUser, UUID trackerId, UUID accountId, MultipartFile icon, String iconColor) {
		Account account = getAccountOrThrow(accountId);
		assertAccountBelongsToTracker(account, trackerId);

		String iconUrl = imageService.uploadImage(icon, "expense-tracker/accounts");
		account.setIconUrl(iconUrl);
		account.setIconColor(iconColor);
		account = accountRepository.save(account);

		log.info("User {} uploaded icon for account '{}' in tracker '{}'",
				currentUser.getEmail(), account.getName(), account.getInstitution().getExpenseTracker().getName());
		return accountMapper.toResponse(account);
	}

	@Override
	@Transactional
	public AccountResponseDto accountDeleteIcon(User currentUser, UUID trackerId, UUID accountId) {
		Account account = getAccountOrThrow(accountId);
		assertAccountBelongsToTracker(account, trackerId);

		account.setIconUrl(null);
		account.setIconColor(null);
		account = accountRepository.save(account);

		log.info("User {} deleted icon for account '{}' in tracker '{}'",
				currentUser.getEmail(), account.getName(), account.getInstitution().getExpenseTracker().getName());
		return accountMapper.toResponse(account);
	}

	private Institution getInstitutionOrThrow(UUID institutionId) {
		return institutionRepository.findById(institutionId)
				.orElseThrow(() -> new EntityNotFoundException("Institution not found"));
	}

	private Account getAccountOrThrow(UUID accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new EntityNotFoundException("Account not found"));
	}

	private void assertInstitutionBelongsToTracker(Institution institution, UUID trackerId) {
		if (!institution.getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Institution not found in this expense tracker");
		}
	}

	private void assertAccountBelongsToTracker(Account account, UUID trackerId) {
		if (!account.getInstitution().getExpenseTracker().getId().equals(trackerId)) {
			throw new EntityNotFoundException("Account not found in this expense tracker");
		}
	}
}