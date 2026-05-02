package org.leoric.expensetracker.budget.mapstruct;

import org.leoric.expensetracker.budget.dto.BudgetPlanResponseDto;
import org.leoric.expensetracker.budget.dto.CategoryActiveBudgetPlanDto;
import org.leoric.expensetracker.budget.dto.CreateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.dto.UpdateBudgetPlanRequestDto;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.mapstruct.AfterMapping;
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
public interface BudgetPlanMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "expenseTracker", ignore = true)
	@Mapping(target = "recurringBudgetTemplate", ignore = true)
	@Mapping(target = "category", ignore = true)
	@Mapping(target = "active", ignore = true)
	@Mapping(target = "createdDate", ignore = true)
	@Mapping(target = "lastModifiedDate", ignore = true)
	@Mapping(target = "createdBy", ignore = true)
	@Mapping(target = "lastModifiedBy", ignore = true)
	@Mapping(target = "currencyCode", expression = "java(dto.currencyCode() != null ? dto.currencyCode().toUpperCase() : null)")
	BudgetPlan toEntity(CreateBudgetPlanRequestDto dto);

	@Mapping(source = "category.id", target = "categoryId")
	@Mapping(source = "category.name", target = "categoryName")
	@Mapping(source = "currencyCode", target = "assetCode")
	@Mapping(target = "assetScale", ignore = true)
	@Mapping(target = "alreadySpent", expression = "java(0L)")
	BudgetPlanResponseDto toResponse(BudgetPlan entity);

	@Mapping(source = "entity.category.id", target = "categoryId")
	@Mapping(source = "entity.category.name", target = "categoryName")
	@Mapping(source = "entity.currencyCode", target = "assetCode")
	@Mapping(target = "assetScale", ignore = true)
	@Mapping(source = "alreadySpent", target = "alreadySpent")
	BudgetPlanResponseDto toResponseWithSpent(BudgetPlan entity, long alreadySpent);

	@Mapping(source = "currencyCode", target = "assetCode")
	@Mapping(target = "assetScale", ignore = true)
	@Mapping(target = "alreadySpent", expression = "java(0L)")
	CategoryActiveBudgetPlanDto toCategoryActiveBudgetPlanDto(BudgetPlan entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(target = "category", ignore = true)
	void updateFromDto(UpdateBudgetPlanRequestDto dto, @MappingTarget BudgetPlan entity);

	@AfterMapping
	default void normalizeUpdate(UpdateBudgetPlanRequestDto dto, @MappingTarget BudgetPlan entity) {
		if (dto.currencyCode() != null) {
			entity.setCurrencyCode(dto.currencyCode().toUpperCase());
		}
	}

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}