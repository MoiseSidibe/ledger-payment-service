package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.TransactionType;

public interface PaymentStrategy {
    TransactionType getType();
    Payment execute(CreatePaymentRequest request, String idempotencyKey);
}

