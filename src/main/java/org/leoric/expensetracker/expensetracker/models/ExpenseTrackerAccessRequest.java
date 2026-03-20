package org.leoric.expensetracker.expensetracker.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestStatus;
import org.leoric.expensetracker.expensetracker.models.constants.ExpenseTrackerAccessRequestType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseTrackerAccessRequest {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(optional = false)
    private User user;

    @ManyToOne(optional = false)
    private ExpenseTracker expenseTracker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseTrackerAccessRequestStatus expenseTrackerAccessRequestStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseTrackerAccessRequestType expenseTrackerAccessRequestType;

    private Instant requestDate;
    private Instant approvalDate;

    @ManyToOne
    private User approvedBy;

    @ManyToOne
    private User invitedBy;

    @PrePersist
    public void prePersist() {
        if (requestDate == null) requestDate = Instant.now();
    }
}