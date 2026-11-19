package org.leoric.expensetracker.workout.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "workout_exercise", uniqueConstraints = @UniqueConstraint(columnNames = {"day_id", "exercise_key"}))
@Getter
@Setter
@NoArgsConstructor
public class WorkoutExercise {
	@Id
	@UuidGenerator
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "day_id", nullable = false)
	private WorkoutDay day;

	@Column(name = "exercise_key", nullable = false, length = 64)
	private String key;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "sets_text", nullable = false, length = 120)
	private String sets;

	@Column(length = 500)
	private String description;

	@Column(length = 2048)
	private String imageUrl;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean completed;
}
