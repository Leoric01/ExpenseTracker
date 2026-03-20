package org.leoric.expensetracker.auth.models;

import jakarta.persistence.*;
import lombok.*;
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