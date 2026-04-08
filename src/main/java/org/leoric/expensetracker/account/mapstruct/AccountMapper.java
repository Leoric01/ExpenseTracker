package org.leoric.expensetracker.account.mapstruct;

import org.leoric.expensetracker.account.dto.AccountResponseDto;
import org.leoric.expensetracker.account.dto.UpdateAccountRequestDto;
import org.leoric.expensetracker.account.models.Account;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface AccountMapper {

	@Mapping(source = "institution.id", target = "institutionId")
	@Mapping(source = "institution.name", target = "institutionName")
	AccountResponseDto toResponse(Account entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	void updateFromDto(UpdateAccountRequestDto dto, @MappingTarget Account entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}