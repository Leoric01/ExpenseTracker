package org.leoric.expensetracker.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

	private static final Set<String> VALID_CURRENCY_CODES = Currency.getAvailableCurrencies().stream()
			.map(Currency::getCurrencyCode)
			.collect(Collectors.toUnmodifiableSet());

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true; // let @NotBlank / @NotNull handle nullability
		}
		return VALID_CURRENCY_CODES.contains(value.toUpperCase());
	}
}