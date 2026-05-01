package org.leoric.expensetracker.category.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.mapstruct.BudgetPlanMapper;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.dto.CategoryActiveTreeResponseDto;
import org.leoric.expensetracker.category.mapstruct.CategoryMapper;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.expensetracker.repositories.ExpenseTrackerRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.image.services.interfaces.ImageService;
import org.leoric.expensetracker.utils.BudgetPlanSpentCalculator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

	@Mock
	private BudgetPlanSpentCalculator budgetPlanSpentCalculator;
	@Mock
	private CategoryRepository categoryRepository;
	@Mock
	private ExpenseTrackerRepository expenseTrackerRepository;
	@Mock
	private BudgetPlanRepository budgetPlanRepository;
	@Mock
	private CategoryMapper categoryMapper;
	@Mock
	private BudgetPlanMapper budgetPlanMapper;
	@Mock
	private ImageService imageService;

	@InjectMocks
	private CategoryServiceImpl categoryService;

	@Test
	void categoryFindAllActiveTree_shouldReturnSortedActiveTreeWithBudgetData() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();

		Category food = Category.builder()
				.id(UUID.randomUUID())
				.name("Food")
				.categoryKind(CategoryKind.EXPENSE)
				.sortOrder(1)
				.active(true)
				.build();
		Category utilities = Category.builder()
				.id(UUID.randomUUID())
				.name("Utilities")
				.categoryKind(CategoryKind.EXPENSE)
				.sortOrder(2)
				.active(true)
				.build();
		Category groceries = Category.builder()
				.id(UUID.randomUUID())
				.name("Groceries")
				.categoryKind(CategoryKind.EXPENSE)
				.parent(food)
				.sortOrder(1)
				.active(true)
				.build();
		Category restaurants = Category.builder()
				.id(UUID.randomUUID())
				.name("Restaurants")
				.categoryKind(CategoryKind.EXPENSE)
				.parent(food)
				.sortOrder(null)
				.active(true)
				.build();

		BudgetPlan olderFoodPlan = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.category(food)
				.name("Food plan old")
				.currencyCode("CZK")
				.validFrom(LocalDate.of(2026, 1, 1))
				.active(true)
				.amount(1000)
				.build();
		BudgetPlan newerFoodPlan = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.category(food)
				.name("Food plan new")
				.currencyCode("CZK")
				.validFrom(LocalDate.of(2026, 3, 1))
				.active(true)
				.amount(2000)
				.build();
		BudgetPlan groceriesPlan = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.category(groceries)
				.name("Groceries plan")
				.currencyCode("EUR")
				.validFrom(LocalDate.of(2026, 2, 1))
				.active(true)
				.amount(500)
				.build();

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId))
				.thenReturn(List.of(restaurants, utilities, groceries, food));
		when(budgetPlanRepository.findAllCurrentActiveByExpenseTrackerIdWithCategory(trackerId, LocalDate.now()))
				.thenReturn(List.of(olderFoodPlan, newerFoodPlan, groceriesPlan));

		List<CategoryActiveTreeResponseDto> result = categoryService.categoryFindAllActiveTree(user, trackerId);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).name()).isEqualTo("Food");
		assertThat(result.get(0).budgetPlanId()).isEqualTo(olderFoodPlan.getId());
		assertThat(result.get(0).budgetPlanName()).isEqualTo("Food plan old");
		assertThat(result.get(0).assetCode()).isEqualTo("CZK");

		assertThat(result.get(0).children()).hasSize(2);
		assertThat(result.get(0).children().get(0).name()).isEqualTo("Groceries");
		assertThat(result.get(0).children().get(0).parentId()).isEqualTo(food.getId());
		assertThat(result.get(0).children().get(0).parentName()).isEqualTo("Food");
		assertThat(result.get(0).children().get(0).budgetPlanName()).isEqualTo("Groceries plan");
		assertThat(result.get(0).children().get(1).name()).isEqualTo("Restaurants");
		assertThat(result.get(0).children().get(1).budgetPlanId()).isNull();

		assertThat(result.get(1).name()).isEqualTo("Utilities");
		assertThat(result.get(1).children()).isEmpty();
	}

	@Test
	void categoryFindAllActiveTree_shouldThrowWhenItemLimitExceeded() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();

		List<Category> categories = IntStream.range(0, 1001)
				.mapToObj(i -> Category.builder()
						.id(UUID.randomUUID())
						.name("Category " + i)
						.categoryKind(CategoryKind.EXPENSE)
						.active(true)
						.build())
				.toList();

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId)).thenReturn(categories);

		assertThatThrownBy(() -> categoryService.categoryFindAllActiveTree(user, trackerId))
				.isInstanceOf(OperationNotPermittedException.class)
				.hasMessageContaining("Limit is 1000 items");
	}
}