package com.alpian.ledger.payment.service.strategy;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.exception.InsufficientFundsException;
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
 * Strategy for DEBIT transactions - money out from an account
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DebitPaymentStrategy implements PaymentStrategy {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public TransactionType getType() {
        return TransactionType.DEBIT;
    }

    @Override
    @Timed(value = "payment.strategy.debit", description = "Time taken to execute debit transaction")
    public Payment execute(CreatePaymentRequest request, String idempotencyKey) {
        String fromAccountId = request.fromAccountId();
        BigDecimal amount = request.amount();

        log.info("Executing DEBIT transaction: {} from account {}", amount, fromAccountId);

        deductBalance(fromAccountId, amount);
        Payment payment = createAndPersistPayment(fromAccountId, amount, idempotencyKey);

        log.info("DEBIT transaction {} completed successfully", payment.getPaymentId());
        return payment;
    }

    private void deductBalance(String fromAccountId, BigDecimal amount) {
        int updated = accountRepository.deductBalance(fromAccountId, amount);

        if (updated == 0) {
            handleDeductionFailure(fromAccountId, amount);
        }
    }

    private void handleDeductionFailure(String fromAccountId, BigDecimal amount) {
        boolean accountExists = accountRepository.findByAccountId(fromAccountId).isPresent();
        if (!accountExists) {
            throw new AccountNotFoundException("Account not found: " + fromAccountId);
        } else {
            throw new InsufficientFundsException(
                String.format("Insufficient funds in account %s for amount %s", fromAccountId, amount));
        }
    }

    private Payment createAndPersistPayment(String fromAccountId, BigDecimal amount, String idempotencyKey) {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment(
                paymentId,
                TransactionType.DEBIT,
                fromAccountId,
                null,
                amount,
                idempotencyKey
        );
        payment.complete();

        PaymentEntity entity = paymentMapper.toEntity(payment);
        paymentRepository.save(entity);

        return payment;
    }
}

