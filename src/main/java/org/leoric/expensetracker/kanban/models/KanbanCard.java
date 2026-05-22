package org.leoric.expensetracker.kanban.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.auth.models.User;
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
@Table(name = "kanban_card", indexes = {
		@Index(name = "idx_kanban_card_stage_order", columnList = "kanban_stage_id, card_order"),
		@Index(name = "idx_kanban_card_stage_due_date", columnList = "kanban_stage_id, due_date"),
		@Index(name = "idx_kanban_card_assigned_user", columnList = "assigned_user_id"),
		@Index(name = "idx_kanban_card_completed_date", columnList = "completed_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class KanbanCard {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "kanban_stage_id", nullable = false)
	private KanbanStage stage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_user_id")
	private User assignedUser;

	@Column(length = 120)
	private String assignedPersonName;

	@Builder.Default
	@OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<KanbanCardTagAssignment> tagAssignments = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<KanbanChecklistItem> checklistItems = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<KanbanCardStageHistory> stageHistory = new ArrayList<>();

	@Column(nullable = false, length = 180)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(length = 2000)
	private String imageUrl;

	@Builder.Default
	@Min(0)
	@Max(10)
	@Column(nullable = false)
	private Integer priority = 0;

	private LocalDate dueDate;

	@Builder.Default
	@Column(nullable = false)
	private Integer estimatedMinutes = 0;

	@Builder.Default
	@Column(nullable = false)
	private Integer actualMinutes = 0;

	@Builder.Default
	@Column(nullable = false)
	private Integer cardOrder = 0;

	private Instant completedDate;

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