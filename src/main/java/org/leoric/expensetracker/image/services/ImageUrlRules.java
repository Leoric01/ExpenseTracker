package org.leoric.expensetracker.image.services;

public final class ImageUrlRules {
	public static final String PATTERN = "^(https?://res\\.cloudinary\\.com/[^\\s]+|/images/expense-tracker/"
			+ "(accounts|institutions|categories|todo|kanban|attachments|workout|restaurants|vacations)/"
			+ "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."
			+ "(jpg|png|webp|heic|heif|gif|avif|pdf))?$";

	private ImageUrlRules() {}

	public static boolean isValid(String url) {
		return url != null && url.matches(PATTERN) && !url.isBlank();
	}
}
