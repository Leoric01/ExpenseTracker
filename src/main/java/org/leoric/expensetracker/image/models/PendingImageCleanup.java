package org.leoric.expensetracker.image.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pending_image_cleanup")
@Getter
@Setter
@NoArgsConstructor
public class PendingImageCleanup {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 2048)
	private String sourceUrl;

	@Column(nullable = false, length = 2048)
	private String destinationUrl;

	@Column(nullable = false)
	private boolean cleaned;

	public PendingImageCleanup(String sourceUrl, String destinationUrl) {
		this.sourceUrl = sourceUrl;
		this.destinationUrl = destinationUrl;
	}
}
