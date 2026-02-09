package com.alpian.ledger.payment.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p FROM PaymentEntity p WHERE p.fromAccountId = :accountId OR p.toAccountId = :accountId " +
           "ORDER BY p.createdAt DESC")
    Page<PaymentEntity> findByAccountId(@Param("accountId") String accountId, Pageable pageable);
}
