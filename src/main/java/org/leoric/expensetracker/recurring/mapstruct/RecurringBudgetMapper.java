package org.leoric.expensetracker.recurring.mapstruct;

import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.recurring.dto.CreateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.dto.RecurringBudgetResponseDto;
import org.leoric.expensetracker.recurring.dto.UpdateRecurringBudgetRequestDto;
import org.leoric.expensetracker.recurring.models.RecurringBudgetTemplate;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface RecurringBudgetMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "expenseTracker", ignore = true)
	@Mapping(target = "category", ignore = true)
	@Mapping(target = "active", ignore = true)
	@Mapping(target = "createdDate", ignore = true)
	@Mapping(target = "lastModifiedDate", ignore = true)
	@Mapping(target = "createdBy", ignore = true)
	@Mapping(target = "lastModifiedBy", ignore = true)
	@Mapping(target = "currencyCode", expression = "java(dto.currencyCode() != null ? dto.currencyCode().toUpperCase() : null)")
	@Mapping(target = "intervalValue", expression = "java(dto.intervalValue() != null ? dto.intervalValue() : 1)")
	@Mapping(source = "startDate", target = "nextRunDate")
	RecurringBudgetTemplate toEntity(CreateRecurringBudgetRequestDto dto);

	@Mapping(source = "category.id", target = "categoryId")
	@Mapping(source = "category.name", target = "categoryName")
	RecurringBudgetResponseDto toResponse(RecurringBudgetTemplate entity);

	@Mapping(target = "id", ignore = true)
	@Mapping(source = "template", target = "recurringBudgetTemplate")
	@Mapping(source = "template.expenseTracker", target = "expenseTracker")
	@Mapping(source = "template.category", target = "category")
	@Mapping(source = "template.name", target = "name")
	@Mapping(source = "template.amount", target = "amount")
	@Mapping(source = "template.currencyCode", target = "currencyCode")
	@Mapping(source = "template.periodType", target = "periodType")
	@Mapping(source = "validFrom", target = "validFrom")
	@Mapping(source = "validTo", target = "validTo")
	@Mapping(target = "active", ignore = true)
	@Mapping(target = "createdDate", ignore = true)
	@Mapping(target = "lastModifiedDate", ignore = true)
	@Mapping(target = "createdBy", ignore = true)
	@Mapping(target = "lastModifiedBy", ignore = true)
	BudgetPlan toBudgetPlan(RecurringBudgetTemplate template, LocalDate validFrom, LocalDate validTo);

	@BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(source = "name", target = "name")
	@Mapping(source = "amount", target = "amount")
	@Mapping(source = "currencyCode", target = "currencyCode")
	@Mapping(source = "periodType", target = "periodType")
	void updateBudgetPlanFromTemplate(RecurringBudgetTemplate template, @MappingTarget BudgetPlan plan);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(target = "category", ignore = true)
	void updateFromDto(UpdateRecurringBudgetRequestDto dto, @MappingTarget RecurringBudgetTemplate entity);

	@AfterMapping
	default void normalizeUpdate(UpdateRecurringBudgetRequestDto dto, @MappingTarget RecurringBudgetTemplate entity) {
		if (dto.currencyCode() != null) {
			entity.setCurrencyCode(dto.currencyCode().toUpperCase());
		}
	}

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}