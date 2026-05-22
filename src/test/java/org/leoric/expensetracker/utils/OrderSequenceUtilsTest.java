package org.leoric.expensetracker.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderSequenceUtilsTest {

	@Test
	void validateContiguous_shouldPassForZeroBasedSequence() {
		List<Item> items = List.of(new Item(0), new Item(1), new Item(2));
		assertThatCode(() -> OrderSequenceUtils.validateContiguous(items, Item::order, "stageOrder"))
				.doesNotThrowAnyException();
	}

	@Test
	void validateContiguous_shouldFailWhenGapExists() {
		List<Item> items = List.of(new Item(0), new Item(2));
		assertThatThrownBy(() -> OrderSequenceUtils.validateContiguous(items, Item::order, "tagOrder"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("contiguous");
	}

	@Test
	void normalizeContiguous_shouldReindexWithoutGaps() {
		List<Item> items = new ArrayList<>(List.of(new Item(4), new Item(1), new Item(7)));
		OrderSequenceUtils.normalizeContiguous(items, Item::order, Item::order);
		assertThat(items).extracting(Item::order).containsExactlyInAnyOrder(0, 1, 2);
	}

	@Test
	void swapOrders_shouldSwapValues() {
		Item first = new Item(0);
		Item second = new Item(3);
		OrderSequenceUtils.swapOrders(first, second, Item::order, Item::order);
		assertThat(first.order()).isEqualTo(3);
		assertThat(second.order()).isEqualTo(0);
	}

	@Test
	void clampTargetOrder_shouldClampBounds() {
		assertThat(OrderSequenceUtils.clampTargetOrder(null, 3)).isEqualTo(0);
		assertThat(OrderSequenceUtils.clampTargetOrder(-5, 3)).isEqualTo(0);
		assertThat(OrderSequenceUtils.clampTargetOrder(99, 3)).isEqualTo(3);
	}

	private static final class Item {
		private int order;

		private Item(int order) {
			this.order = order;
		}

		private Integer order() {
			return order;
		}

		private void order(Integer value) {
			this.order = value;
		}
	}
}