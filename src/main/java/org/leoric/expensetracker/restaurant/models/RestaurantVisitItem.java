package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_visit_item", indexes = @Index(columnList = "visit_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class RestaurantVisitItem {
	@Id @UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "visit_id", nullable = false)
	private RestaurantVisit visit;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dish_id")
	private RestaurantDish dish;
	@Column(nullable = false, length = 180)
	private String nameSnapshot;
	private Long priceMinor;
	private Integer rating;
	@Column(length = 2000)
	private String note;
	@CreatedDate @Column(updatable = false)
	private Instant createdDate;
	@LastModifiedDate
	private Instant lastModifiedDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
	@LastModifiedBy
	private UUID lastModifiedBy;
}
