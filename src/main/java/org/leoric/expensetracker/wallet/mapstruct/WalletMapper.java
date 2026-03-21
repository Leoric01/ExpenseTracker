package org.leoric.expensetracker.wallet.mapstruct;

import org.leoric.expensetracker.wallet.dto.UpdateWalletRequestDto;
import org.leoric.expensetracker.wallet.dto.WalletResponseDto;
import org.leoric.expensetracker.wallet.models.Wallet;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface WalletMapper {

	WalletResponseDto toResponse(Wallet entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	void updateFromDto(UpdateWalletRequestDto dto, @MappingTarget Wallet entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}