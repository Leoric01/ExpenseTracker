package org.leoric.expensetracker.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_item_photo", indexes = @Index(columnList = "item_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor
public class RestaurantItemPhoto {
	@Id @UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private RestaurantVisitItem item;
	@Column(nullable = false, length = 2000)
	private String imageUrl;
	@CreatedDate @Column(updatable = false)
	private Instant createdDate;
	@CreatedBy @Column(updatable = false)
	private UUID createdBy;
}
