package org.leoric.expensetracker.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.springframework.stereotype.Component;

@Component
public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

	private final AssetRepository assetRepository;

	public CurrencyCodeValidator(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true; // let @NotBlank / @NotNull handle nullability
		}
		return assetRepository.existsByCodeIgnoreCase(value);
	}
}