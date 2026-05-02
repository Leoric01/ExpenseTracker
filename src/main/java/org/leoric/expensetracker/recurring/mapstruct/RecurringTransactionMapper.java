package org.leoric.expensetracker.recurring.mapstruct;

import org.leoric.expensetracker.recurring.dto.RecurringTransactionResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringTransactionRequestDto;
import org.leoric.expensetracker.recurring.models.RecurringTransactionTemplate;
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
public interface RecurringTransactionMapper {

	@Mapping(source = "holding.id", target = "holdingId")
	@Mapping(source = "holding.account.name", target = "holdingName")
	@Mapping(source = "holding.asset.scale", target = "assetScale")
	@Mapping(source = "category.id", target = "categoryId")
	@Mapping(source = "category.name", target = "categoryName")
	@Mapping(source = "currencyCode", target = "assetCode")
	RecurringTransactionResponseDto toResponse(RecurringTransactionTemplate entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(target = "holding", ignore = true)
	@Mapping(target = "category", ignore = true)
	void updateFromDto(UpdateRecurringTransactionRequestDto dto, @MappingTarget RecurringTransactionTemplate entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}