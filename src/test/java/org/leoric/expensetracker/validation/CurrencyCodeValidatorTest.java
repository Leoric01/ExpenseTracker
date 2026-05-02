package org.leoric.expensetracker.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.asset.repositories.AssetRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyCodeValidatorTest {

	@Mock
	private AssetRepository assetRepository;

	@InjectMocks
	private CurrencyCodeValidator validator;

	@Test
	void isValid_shouldReturnTrueForNull() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@Test
	void isValid_shouldReturnTrueForLowercaseValidCode() {
		when(assetRepository.existsByCodeIgnoreCase("usd")).thenReturn(true);

		assertThat(validator.isValid("usd", null)).isTrue();
	}

	@Test
	void isValid_shouldReturnFalseForUnknownCode() {
		when(assetRepository.existsByCodeIgnoreCase("ZZZ")).thenReturn(false);

		assertThat(validator.isValid("ZZZ", null)).isFalse();
	}
}