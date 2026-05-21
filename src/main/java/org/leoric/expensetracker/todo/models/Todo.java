package org.leoric.expensetracker.todo.models;

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
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;
import org.leoric.expensetracker.todo.models.constants.TodoPriority;
import org.leoric.expensetracker.todo.models.constants.TodoStatus;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "todo", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"expense_tracker_id", "parent_todo_id", "title", "is_deleted"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Todo {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_todo_id")
	private Todo parent;

	@Builder.Default
	@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
	private List<Todo> children = new ArrayList<>();

	@Column(nullable = false, length = 180)
	private String title;

	@Column(length = 1000)
	private String description;

	@Column(length = 2000)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TodoStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TodoPriority priority;

	private LocalDate dueDate;

	private Instant completedAt;

	private Instant archivedAt;

	private Integer estimatedMinutes;

	@Column(length = 1000)
	private String note;

	@Builder.Default
	@Column(nullable = false)
	private Integer sortOrder = 0;

	@Builder.Default
	@Column(nullable = false)
	private boolean active = true;

	@Builder.Default
	@Column(name = "is_deleted", nullable = false)
	private boolean isDeleted = false;

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