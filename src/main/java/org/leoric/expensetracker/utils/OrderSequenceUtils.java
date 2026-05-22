package org.leoric.expensetracker.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class OrderSequenceUtils {

	private OrderSequenceUtils() {
	}

	public static <T> void validateContiguous(
			List<T> items,
			Function<T, Integer> orderGetter,
			String fieldName
	) {
		List<Integer> sorted = items.stream()
				.map(orderGetter)
				.sorted()
				.toList();

		for (int i = 0; i < sorted.size(); i++) {
			Integer actual = sorted.get(i);
			if (actual == null || actual != i) {
				throw new IllegalArgumentException("Invalid %s sequence. Expected contiguous values 0..n-1".formatted(fieldName));
			}
		}
	}

	public static <T> void normalizeContiguous(
			List<T> items,
			Function<T, Integer> orderGetter,
			BiConsumer<T, Integer> orderSetter
	) {
		List<T> sorted = new ArrayList<>(items);
		sorted.sort(Comparator.comparing(item -> nullSafe(orderGetter.apply(item))));
		for (int i = 0; i < sorted.size(); i++) {
			orderSetter.accept(sorted.get(i), i);
		}
	}

	public static <T> void swapOrders(
			T first,
			T second,
			Function<T, Integer> orderGetter,
			BiConsumer<T, Integer> orderSetter
	) {
		if (first == null || second == null || first == second) {
			return;
		}

		Integer firstOrder = orderGetter.apply(first);
		Integer secondOrder = orderGetter.apply(second);
		if (firstOrder == null || secondOrder == null) {
			throw new IllegalArgumentException("Order value must not be null when swapping");
		}

		orderSetter.accept(first, secondOrder);
		orderSetter.accept(second, firstOrder);
	}

	public static int clampTargetOrder(Integer targetOrder, int size) {
		if (size < 0) {
			throw new IllegalArgumentException("Size must be >= 0");
		}
		if (targetOrder == null || targetOrder < 0) {
			return 0;
		}
		return Math.min(targetOrder, size);
	}

	private static int nullSafe(Integer value) {
		return value == null ? Integer.MAX_VALUE : value;
	}
}