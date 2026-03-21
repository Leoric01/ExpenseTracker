package org.leoric.expensetracker.category.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.category.models.constants.CategoryKind;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "category", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"expense_tracker_id", "parent_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Category {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;

	@ManyToOne(fetch = FetchType.LAZY)
	private Category parent;

	@Builder.Default
	@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
	private List<Category> children = new ArrayList<>();

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CategoryKind categoryKind;

	private Integer sortOrder;

	private String iconUrl;

	@Column(length = 7)
	private String iconColor;

	@Builder.Default
	@Column(nullable = false)
	private boolean active = true;

	@CreatedDate
	@Column(updatable = false, nullable = false)
	private Instant createdDate;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant lastModifiedDate;

	@CreatedBy
	@Column(updatable = false)
	private UUID createdBy;

	@LastModifiedBy
	private UUID lastModifiedBy;
}