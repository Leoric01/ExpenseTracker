package org.leoric.expensetracker.expensetracker.mapstruct;

import org.leoric.expensetracker.expensetracker.dto.ExpenseTrackerResponse;
import org.leoric.expensetracker.expensetracker.dto.UpdateExpenseTrackerRequest;
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
	ExpenseTrackerResponse toResponse(ExpenseTracker entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	void updateFromDto(UpdateExpenseTrackerRequest dto, @MappingTarget ExpenseTracker entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}