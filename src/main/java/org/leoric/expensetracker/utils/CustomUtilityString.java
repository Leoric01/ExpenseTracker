package org.leoric.expensetracker.utils;

import java.text.Normalizer;
import java.util.Locale;

public class CustomUtilityString {

	public static String normalize(String input) {
		if (input == null) {
			return null;
		}

		return Normalizer.normalize(input, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
				.toLowerCase(Locale.ROOT);
	}
}