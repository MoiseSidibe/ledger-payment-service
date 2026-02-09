package com.alpian.ledger.payment.infrastructure.persistence;

import com.alpian.ledger.payment.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(EventStatus status);
    List<OutboxEventEntity> findByAggregateId(String aggregateId);
    List<OutboxEventEntity> findByStatus(EventStatus status);
}
