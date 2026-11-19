package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "restaurant_file_cleanup")
@Getter @Setter @NoArgsConstructor
public class RestaurantFileCleanup {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, length = 2000)
	private String imageUrl;
	public RestaurantFileCleanup(String imageUrl) { this.imageUrl = imageUrl; }
}
