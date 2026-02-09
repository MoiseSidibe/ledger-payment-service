package com.alpian.ledger.payment.infrastructure.mapper;

import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    default Payment toDomain(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }

        Payment payment = new Payment(
                entity.getPaymentId(),
                entity.getType(),
                entity.getFromAccountId(),
                entity.getToAccountId(),
                entity.getAmount(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt()
        );

        applyStatusTransition(payment, entity.getStatus());

        return payment;
    }

    default PaymentEntity toEntity(Payment payment) {
        if (payment == null) {
            return null;
        }

        PaymentEntity entity = new PaymentEntity();
        entity.setPaymentId(payment.getPaymentId());
        entity.setType(payment.getType());
        entity.setFromAccountId(payment.getFromAccountId());
        entity.setToAccountId(payment.getToAccountId());
        entity.setAmount(payment.getAmount());
        entity.setStatus(payment.getStatus());
        entity.setIdempotencyKey(payment.getIdempotencyKey());

        return entity;
    }

    private void applyStatusTransition(Payment payment, PaymentStatus targetStatus) {
        if (payment.getStatus() == targetStatus) {
            return;
        }

        switch (targetStatus) {
            case COMPLETED -> payment.complete();
            case FAILED -> payment.fail();
            case CREATED -> {}
        }
    }
}
