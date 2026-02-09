package com.alpian.ledger.payment.api;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.api.dto.PaymentHistoryResponse;
import com.alpian.ledger.payment.api.dto.PaymentResponse;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.service.PaymentService;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for payment operations
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Payment API", description = "Endpoints for payment operations")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create a transaction", description = "Creates a transaction (DEBIT, CREDIT, or INTERNAL_TRANSFER)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transaction created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient funds"),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Timed(value = "api.payment.create", description = "Time taken to process payment creation API request")
    @Counted(value = "api.payment.create.count", description = "Number of payment creation API requests")
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Unique idempotency key (UUID) for the request", required = true)
            @RequestHeader("Idempotency-Key") @NotNull UUID idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        log.info("Received {} transaction request with idempotency key {}",
                 request.type(), idempotencyKey);

        Payment payment = paymentService.createPayment(request, idempotencyKey.toString());
        PaymentResponse response = buildPaymentResponse(payment);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/history/{accountId}")
    @Operation(summary = "Get payment history", description = "Retrieves paginated payment history for an account, sorted by creation date (newest first)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @Timed(value = "api.payment.history", description = "Time taken to process payment history API request")
    public ResponseEntity<PaymentHistoryResponse> getPaymentHistory(
            @Parameter(description = "Account ID", required = true)
            @PathVariable String accountId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("Fetching payment history for account {} - page: {}, size: {}", accountId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Payment> paymentPage = paymentService.getPaymentHistory(accountId, pageable);

        List<PaymentResponse> paymentResponses = buildPaymentResponses(paymentPage, accountId);
        PaymentHistoryResponse response = buildHistoryResponse(paymentPage, paymentResponses);

        return ResponseEntity.ok(response);
    }

    // Private helper methods

    private PaymentResponse buildPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getType(),
                payment.getFromAccountId(),
                payment.getToAccountId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }

    private List<PaymentResponse> buildPaymentResponses(Page<Payment> paymentPage, String accountId) {
        return paymentPage.getContent().stream()
                .map(payment -> buildPaymentResponseWithDirection(payment, accountId))
                .collect(Collectors.toList());
    }

    private PaymentResponse buildPaymentResponseWithDirection(Payment payment, String accountId) {
        String direction = determineDirection(payment, accountId);
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getType(),
                payment.getFromAccountId(),
                payment.getToAccountId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                direction
        );
    }

    private PaymentHistoryResponse buildHistoryResponse(Page<Payment> paymentPage,
                                                        List<PaymentResponse> paymentResponses) {
        return new PaymentHistoryResponse(
                paymentResponses,
                paymentPage.getNumber(),
                paymentPage.getSize(),
                paymentPage.getNumberOfElements(),
                paymentPage.getTotalElements(),
                paymentPage.getTotalPages()
        );
    }

    private String determineDirection(Payment payment, String queriedAccountId) {
        return switch (payment.getType()) {
            case DEBIT -> "OUT";
            case CREDIT -> "IN";
            case INTERNAL_TRANSFER -> {
                if (queriedAccountId.equals(payment.getFromAccountId())) {
                    yield "OUT";
                } else if (queriedAccountId.equals(payment.getToAccountId())) {
                    yield "IN";
                } else {
                    yield null;
                }
            }
        };
    }
}
