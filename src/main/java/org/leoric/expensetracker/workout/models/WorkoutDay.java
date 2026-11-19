package org.leoric.expensetracker.workout.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workout_day", uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "day_key"}))
@Getter
@Setter
@NoArgsConstructor
public class WorkoutDay {
	@Id
	@UuidGenerator
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private WorkoutPlan plan;

	@Column(name = "day_key", nullable = false, length = 64)
	private String key;

	@Column(nullable = false, length = 40)
	private String label;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 255)
	private String focus;

	@Column(nullable = false)
	private int sortOrder;

	@OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<WorkoutExercise> exercises = new ArrayList<>();
}
