package org.leoric.expensetracker.category.mapstruct;

import org.leoric.expensetracker.category.dto.CategoryResponseDto;
import org.leoric.expensetracker.category.dto.UpdateCategoryRequestDto;
import org.leoric.expensetracker.category.models.Category;
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
public interface CategoryMapper {

	@Mapping(source = "parent.id", target = "parentId")
	@Mapping(source = "parent.name", target = "parentName")
	CategoryResponseDto toResponse(Category entity);

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
			unmappedTargetPolicy = ReportingPolicy.IGNORE)
	@Mapping(target = "parent", ignore = true)
	void updateFromDto(UpdateCategoryRequestDto dto, @MappingTarget Category entity);

	default OffsetDateTime map(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}
}