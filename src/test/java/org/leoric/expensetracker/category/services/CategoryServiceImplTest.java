package org.leoric.expensetracker.category.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.budget.models.BudgetPlan;
import org.leoric.expensetracker.budget.repositories.BudgetPlanRepository;
import org.leoric.expensetracker.category.dto.CategoryActivePageResponse;
import org.leoric.expensetracker.category.dto.CategoryActiveRowResponse;
import org.leoric.expensetracker.category.dto.CategoryActiveTreeResponseDto;
import org.leoric.expensetracker.category.models.Category;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.category.repositories.CategoryRepository;
import org.leoric.expensetracker.handler.exceptions.OperationNotPermittedException;
import org.leoric.expensetracker.utils.BudgetPlanSpentCalculator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

	@Mock
	private CategoryRepository categoryRepository;
	@Mock
	private BudgetPlanRepository budgetPlanRepository;
	@Mock
	private BudgetPlanSpentCalculator budgetPlanSpentCalculator;
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
		assertThat(result.getFirst().name()).isEqualTo("Food");
		assertThat(result.getFirst().budgetPlanId()).isEqualTo(olderFoodPlan.getId());
		assertThat(result.getFirst().budgetPlanName()).isEqualTo("Food plan old");
		assertThat(result.getFirst().assetCode()).isEqualTo("CZK");

		assertThat(result.getFirst().children()).hasSize(2);
		assertThat(result.getFirst().children().getFirst().name()).isEqualTo("Groceries");
		assertThat(result.getFirst().children().getFirst().parentId()).isEqualTo(food.getId());
		assertThat(result.getFirst().children().getFirst().parentName()).isEqualTo("Food");
		assertThat(result.getFirst().children().getFirst().budgetPlanName()).isEqualTo("Groceries plan");
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

	@Test
	void categoryFindAllActiveLight_shouldReturnSlimRowsAndMetadata() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();

		Category category = Category.builder()
				.id(UUID.randomUUID())
				.name("Food")
				.categoryKind(CategoryKind.EXPENSE)
				.sortOrder(2)
				.active(true)
				.build();

		BudgetPlan plan = BudgetPlan.builder()
				.id(UUID.randomUUID())
				.category(category)
				.name("Food plan")
				.currencyCode("CZK")
				.validFrom(LocalDate.of(2026, 5, 1))
				.validTo(LocalDate.of(2026, 5, 31))
				.active(true)
				.amount(3000)
				.build();

		when(categoryRepository.findByExpenseTrackerIdAndActiveTrue(trackerId, PageRequest.of(0, 10)))
				.thenReturn(new PageImpl<>(List.of(category), PageRequest.of(0, 10), 1));
		when(budgetPlanRepository.findAllActiveByExpenseTrackerIdWithCategoryInRange(
				trackerId,
				LocalDate.of(2026, 5, 1),
				LocalDate.of(2026, 5, 31)))
				.thenReturn(List.of(plan));
		when(budgetPlanSpentCalculator.computeAlreadySpent(plan)).thenReturn(1200L);

		CategoryActivePageResponse result = categoryService.categoryFindAllActiveLight(
				user,
				trackerId,
				null,
				LocalDate.of(2026, 5, 1),
				LocalDate.of(2026, 5, 31),
				PageRequest.of(0, 10));

		assertThat(result.page().page()).isEqualTo(0);
		assertThat(result.page().size()).isEqualTo(10);
		assertThat(result.page().totalElements()).isEqualTo(1);
		assertThat(result.page().totalPages()).isEqualTo(1);

		assertThat(result.categories()).hasSize(1);
		assertThat(result.categories().getFirst().id()).isEqualTo(category.getId().toString());
		assertThat(result.categories().getFirst().budgetPlansForPeriod()).hasSize(1);
		assertThat(result.categories().getFirst().budgetPlansForPeriod().getFirst().alreadySpent()).isEqualTo(1200L);
	}

	@Test
	void categoryFindAllActiveLight_shouldIncludeParentChainWhenSearchProvided() {
		UUID trackerId = UUID.randomUUID();
		User user = User.builder().id(UUID.randomUUID()).email("tester@example.com").build();

		Category parent = Category.builder()
				.id(UUID.randomUUID())
				.name("Food")
				.categoryKind(CategoryKind.EXPENSE)
				.sortOrder(1)
				.active(true)
				.build();
		Category child = Category.builder()
				.id(UUID.randomUUID())
				.name("Restaurants")
				.categoryKind(CategoryKind.EXPENSE)
				.parent(parent)
				.sortOrder(2)
				.active(true)
				.build();

		when(categoryRepository.findActiveByExpenseTrackerIdWithSearch(trackerId, "rest", PageRequest.of(0, 10)))
				.thenReturn(new PageImpl<>(List.of(child), PageRequest.of(0, 10), 1));
		when(categoryRepository.findByExpenseTrackerIdAndActiveTrueAndIdIn(any(), anyCollection()))
				.thenReturn(List.of(parent));

		CategoryActivePageResponse result = categoryService.categoryFindAllActiveLight(
				user,
				trackerId,
				"rest",
				null,
				null,
				PageRequest.of(0, 10));

		assertThat(result.categories()).hasSize(2);
		assertThat(result.categories().stream().map(CategoryActiveRowResponse::id).toList())
				.contains(parent.getId().toString(), child.getId().toString());
		assertThat(result.categories().stream()
				.filter(c -> c.id().equals(child.getId().toString()))
				.findFirst()
				.orElseThrow()
				.parentId())
				.isEqualTo(parent.getId().toString());
	}
}