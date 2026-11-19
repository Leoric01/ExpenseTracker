package org.leoric.expensetracker.image.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "image_storage_setting")
@Getter
@Setter
@NoArgsConstructor
public class ImageStorageSetting {
	@Id
	private Integer id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ImageStorageProvider provider;

	public ImageStorageSetting(ImageStorageProvider provider) {
		this.id = 1;
		this.provider = provider;
	}
}
