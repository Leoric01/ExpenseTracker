package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import java.util.UUID;

@Entity
@Table(name = "restaurant_service_catalog", indexes = @Index(columnList = "expense_tracker_id"))
@Getter @Setter @NoArgsConstructor
public class RestaurantServiceTag {
	@Id @UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;
	@Column(nullable = false, length = 80)
	private String name;
}
