package org.leoric.expensetracker.workout.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.expensetracker.models.ExpenseTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workout_plan", uniqueConstraints = @UniqueConstraint(columnNames = "expense_tracker_id"))
@Getter
@Setter
@NoArgsConstructor
public class WorkoutPlan {
	@Id
	@UuidGenerator
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_tracker_id", nullable = false)
	private ExpenseTracker expenseTracker;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, length = 120)
	private String weekLabel;

	@OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<WorkoutDay> days = new ArrayList<>();
}
