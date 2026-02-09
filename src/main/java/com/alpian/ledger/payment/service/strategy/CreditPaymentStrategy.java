package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.infrastructure.mapper.PaymentMapper;
import com.alpian.ledger.payment.infrastructure.persistence.AccountRepository;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentEntity;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Strategy for CREDIT transactions - money in to an account (deposit)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditPaymentStrategy implements PaymentStrategy {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public TransactionType getType() {
        return TransactionType.CREDIT;
    }

    @Override
    @Timed(value = "payment.strategy.credit", description = "Time taken to execute credit transaction")
    public Payment execute(CreatePaymentRequest request, String idempotencyKey) {
        String toAccountId = request.toAccountId();
        BigDecimal amount = request.amount();

        log.info("Executing CREDIT transaction: {} to account {}", amount, toAccountId);

        creditBalance(toAccountId, amount);
        Payment payment = createAndPersistPayment(toAccountId, amount, idempotencyKey);

        log.info("CREDIT transaction {} completed successfully", payment.getPaymentId());
        return payment;
    }

    private void creditBalance(String toAccountId, BigDecimal amount) {
        int updated = accountRepository.creditBalance(toAccountId, amount);

        if (updated == 0) {
            throw new AccountNotFoundException("Account not found: " + toAccountId);
        }
    }

    private Payment createAndPersistPayment(String toAccountId, BigDecimal amount, String idempotencyKey) {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment(
                paymentId,
                TransactionType.CREDIT,
                null,
                toAccountId,
                amount,
                idempotencyKey
        );
        payment.complete();

        PaymentEntity entity = paymentMapper.toEntity(payment);
        paymentRepository.save(entity);

        return payment;
    }
}

