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
import java.util.List;
import java.util.UUID;

/**
 * Strategy for INTERNAL_TRANSFER transactions - between two accounts
 * Uses explicit locking in alphabetical order to prevent deadlocks
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTransferPaymentStrategy implements PaymentStrategy {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public TransactionType getType() {
        return TransactionType.INTERNAL_TRANSFER;
    }

    @Override
    @Timed(value = "payment.strategy.internal_transfer", description = "Time taken to execute internal transfer")
    public Payment execute(CreatePaymentRequest request, String idempotencyKey) {
        String fromAccountId = request.fromAccountId();
        String toAccountId = request.toAccountId();
        BigDecimal amount = request.amount();

        log.info("Executing INTERNAL_TRANSFER: {} from {} to {}", amount, fromAccountId, toAccountId);

        lockBothAccounts(fromAccountId, toAccountId);
        performTransfer(fromAccountId, toAccountId, amount);
        Payment payment = createAndPersistPayment(fromAccountId, toAccountId, amount, idempotencyKey);

        log.info("INTERNAL_TRANSFER {} completed successfully", payment.getPaymentId());
        return payment;
    }

    private void lockBothAccounts(String fromAccountId, String toAccountId) {
        String firstAccountId = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
        String secondAccountId = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

        List<String> lockedAccounts = accountRepository.lockAccountsInOrder(firstAccountId, secondAccountId);

        if (lockedAccounts.size() != 2) {
            validateBothAccountsExist(fromAccountId, toAccountId);
        }
    }

    private void validateBothAccountsExist(String fromAccountId, String toAccountId) {
        boolean fromExists = accountRepository.findByAccountId(fromAccountId).isPresent();
        if (!fromExists) {
            throw new AccountNotFoundException("Account not found: " + fromAccountId);
        } else {
            throw new AccountNotFoundException("Account not found: " + toAccountId);
        }
    }

    private void performTransfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        deductFromSource(fromAccountId, amount);
        creditToDestination(toAccountId, amount);
    }

    private void deductFromSource(String fromAccountId, BigDecimal amount) {
        int deducted = accountRepository.deductBalance(fromAccountId, amount);
        if (deducted == 0) {
            throw new InsufficientFundsException(
                String.format("Insufficient funds in account %s for transfer amount %s", fromAccountId, amount));
        }
    }

    private void creditToDestination(String toAccountId, BigDecimal amount) {
        int credited = accountRepository.creditBalance(toAccountId, amount);
        if (credited == 0) {
            throw new AccountNotFoundException("Account not found: " + toAccountId);
        }
    }

    private Payment createAndPersistPayment(String fromAccountId, String toAccountId,
                                           BigDecimal amount, String idempotencyKey) {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = new Payment(
                paymentId,
                TransactionType.INTERNAL_TRANSFER,
                fromAccountId,
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

