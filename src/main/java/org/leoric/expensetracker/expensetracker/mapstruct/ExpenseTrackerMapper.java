package org.leoric.expensetracker.expensetracker.mapstruct;

import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerMineResponseDto;
import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponseDto;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequestDto;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
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
public interface ExpenseTrackerMapper {

	@Mapping(source = "createdByOwner.fullname", target = "ownerFullName")
	ExpenseTrackerResponseDto toResponse(ExpenseTracker entity);

	@Mapping(source = "entity.createdByOwner.fullname", target = "ownerFullName")
	@Mapping(source = "role", target = "role")
	ExpenseTrackerMineResponseDto toMineResponse(ExpenseTracker entity, String role);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	void updateFromDto(UpdateExpenseTrackerRequestDto dto, @MappingTarget ExpenseTracker entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}