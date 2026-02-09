package com.alpian.ledger.payment.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
public class Payment {
    private final String paymentId;
    private final TransactionType type;
    private final String fromAccountId;
    private final String toAccountId;
    private final BigDecimal amount;
    private PaymentStatus status;
    private final String idempotencyKey;
    private final Instant createdAt;

    public Payment(String paymentId, TransactionType type, String fromAccountId,
                   String toAccountId, BigDecimal amount, String idempotencyKey, Instant createdAt) {
        this.paymentId = paymentId;
        this.type = type;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.CREATED;
        this.createdAt = createdAt;}

    public Payment(String paymentId, TransactionType type, String fromAccountId,
                   String toAccountId, BigDecimal amount, String idempotencyKey) {
        this(paymentId, type, fromAccountId, toAccountId, amount, idempotencyKey, Instant.now());
    }

    public void complete() {
        if (this.status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Payment can only be completed from CREATED state");
        }
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail() {
        if (this.status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Payment can only be failed from CREATED state");
        }
        this.status = PaymentStatus.FAILED;
    }

}
