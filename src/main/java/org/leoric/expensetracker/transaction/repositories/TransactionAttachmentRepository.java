package org.leoric.expensetracker.transaction.repositories;

import org.leoric.expensetracker.transaction.models.TransactionAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionAttachmentRepository extends JpaRepository<TransactionAttachment, UUID> {

	List<TransactionAttachment> findByTransactionId(UUID transactionId);
}