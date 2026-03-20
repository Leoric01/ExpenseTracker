package org.leoric.expensetracker.auth.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "navbar_favourites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavbarFavourite {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.AUTO)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false)
	private String itemKey; // např. route nebo identifikátor FE položky

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
}