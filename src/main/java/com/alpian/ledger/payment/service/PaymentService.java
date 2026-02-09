package com.alpian.ledger.payment.service;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.exception.IdempotencyConflictException;
import com.alpian.ledger.payment.infrastructure.mapper.PaymentMapper;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentEntity;
import com.alpian.ledger.payment.infrastructure.persistence.PaymentRepository;
import com.alpian.ledger.payment.infrastructure.persistence.*;
import com.alpian.ledger.payment.service.strategy.PaymentStrategy;
import com.alpian.ledger.payment.service.strategy.PaymentStrategyFactory;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Slf4j
@Validated
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventService outboxEventService;
    private final PaymentMapper paymentMapper;
    private final PaymentStrategyFactory strategyFactory;
    private final AccountService accountService;

    @Transactional
    @Timed(value = "payment.create", description = "Time taken to create a payment", extraTags = {"type", "#{#request.type().name()}"})
    @Counted(value = "payment.create.count", description = "Number of payment creation attempts")
    public Payment createPayment(@Valid @NotNull CreatePaymentRequest request, @NotNull String idempotencyKey) {
        log.info("Creating {} transaction with idempotency key {}",
                 request.type(), idempotencyKey);
        validateIdempotency(idempotencyKey);
        PaymentStrategy strategy = strategyFactory.getStrategy(request.type());
        Payment payment = strategy.execute(request, idempotencyKey);
        outboxEventService.publishPaymentEvent(payment);
        log.info("Payment {} ({}) created successfully", payment.getPaymentId(), request.type());
        return payment;
    }

    @Transactional(readOnly = true)
    @Timed(value = "payment.history.fetch", description = "Time taken to fetch payment history")
    public Page<Payment> getPaymentHistory(@NotNull String accountId, Pageable pageable) {
        log.info("Fetching payment history for account {} with page {}", accountId, pageable.getPageNumber());

        Page<PaymentEntity> paymentEntities = paymentRepository.findByAccountId(accountId, pageable);

        if (paymentEntities.isEmpty()) {
            //Throws account not found error if account doesn't exist
            accountService.getAccount(accountId);
        }
        return paymentEntities.map(paymentMapper::toDomain);
    }

    private void validateIdempotency(String idempotencyKey) {
        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.warn("Payment with idempotency key {} already exists", idempotencyKey);
            throw new IdempotencyConflictException("Payment with this idempotency key already exists");
        }
    }
}
