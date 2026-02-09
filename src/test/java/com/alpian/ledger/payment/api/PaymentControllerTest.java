package com.alpian.ledger.payment.api;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.config.JacksonConfig;
import com.alpian.ledger.payment.domain.Payment;
import com.alpian.ledger.payment.domain.TransactionType;
import com.alpian.ledger.payment.exception.AccountNotFoundException;
import com.alpian.ledger.payment.exception.IdempotencyConflictException;
import com.alpian.ledger.payment.exception.InsufficientFundsException;
import com.alpian.ledger.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({JacksonConfig.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    private UUID idempotencyKey;
    private CreatePaymentRequest debitRequest;
    private Payment mockPayment;

    @BeforeEach
    void setUp() {
        idempotencyKey = UUID.randomUUID();
        debitRequest = new CreatePaymentRequest(
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("100.00")
        );

        mockPayment = new Payment(
                "PAY-123",
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("100.00"),
                idempotencyKey.toString(),
                Instant.parse("2026-02-08T10:00:00Z")
        );
        mockPayment.complete();
    }

    @Test
    void shouldReturnCreatedWhenValidDebitRequest() throws Exception {
        // Given
        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey.toString())))
                .thenReturn(mockPayment);

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("PAY-123"))
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.fromAccountId").value("ACC-001"))
                .andExpect(jsonPath("$.toAccountId").doesNotExist())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnCreatedWhenValidCreditRequest() throws Exception {
        // Given
        CreatePaymentRequest creditRequest = new CreatePaymentRequest(
                TransactionType.CREDIT,
                null,
                "ACC-002",
                new BigDecimal("50.00")
        );

        Payment creditPayment = new Payment(
                "PAY-456",
                TransactionType.CREDIT,
                null,
                "ACC-002",
                new BigDecimal("50.00"),
                idempotencyKey.toString(),
                Instant.now()
        );
        creditPayment.complete();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey.toString())))
                .thenReturn(creditPayment);

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.fromAccountId").doesNotExist())
                .andExpect(jsonPath("$.toAccountId").value("ACC-002"));
    }

    @Test
    void shouldReturnCreatedWhenValidInternalTransferRequest() throws Exception {
        // Given
        CreatePaymentRequest transferRequest = new CreatePaymentRequest(
                TransactionType.INTERNAL_TRANSFER,
                "ACC-001",
                "ACC-002",
                new BigDecimal("75.00")
        );

        Payment transferPayment = new Payment(
                "PAY-789",
                TransactionType.INTERNAL_TRANSFER,
                "ACC-001",
                "ACC-002",
                new BigDecimal("75.00"),
                idempotencyKey.toString(),
                Instant.now()
        );
        transferPayment.complete();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey.toString())))
                .thenReturn(transferPayment);

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.fromAccountId").value("ACC-001"))
                .andExpect(jsonPath("$.toAccountId").value("ACC-002"));
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyMissing() throws Exception {
        // When/Then
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"));
    }

    @Test
    void shouldReturnBadRequestWhenTypeIsNull() throws Exception {
        // Given
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest(
                null,
                "ACC-001",
                null,
                new BigDecimal("100.00")
        );

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsNegative() throws Exception {
        // Given
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest(
                TransactionType.DEBIT,
                "ACC-001",
                null,
                new BigDecimal("-100.00")
        );

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenInvalidEnumValue() throws Exception {
        // Given
        String invalidJson = """
                {
                    "type": "INVALID_TYPE",
                    "fromAccountId": "ACC-001",
                    "amount": 100.00
                }
                """;

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenInsufficientFunds() throws Exception {
        // Given
        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey.toString())))
                .thenThrow(new InsufficientFundsException("Insufficient funds"));

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void shouldReturnBadRequestWhenAccountNotFound() throws Exception {
        // Given
        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey.toString())))
                .thenThrow(new AccountNotFoundException("ACC-001"));

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictWhenIdempotencyConflict() throws Exception {
        // Given
        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey.toString())))
                .thenThrow(new IdempotencyConflictException("Idempotency conflict"));

        // When/Then
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void shouldReturnPagedHistoryWhenValidRequest() throws Exception {
        // Given
        Payment payment1 = new Payment("PAY-1", TransactionType.DEBIT, "ACC-001", null,
                new BigDecimal("100.00"), "key1", Instant.now());
        payment1.complete();

        Payment payment2 = new Payment("PAY-2", TransactionType.CREDIT, null, "ACC-001",
                new BigDecimal("50.00"), "key2", Instant.now());
        payment2.complete();

        PageRequest pageRequest = PageRequest.of(0, 20);
        Page<Payment> paymentPage = new PageImpl<>(Arrays.asList(payment1, payment2), pageRequest, 2);

        when(paymentService.getPaymentHistory(eq("ACC-001"), any()))
                .thenReturn(paymentPage);

        // When/Then
        mockMvc.perform(get("/payments/history/ACC-001")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments", hasSize(2)))
                .andExpect(jsonPath("$.payments[0].paymentId").value("PAY-1"))
                .andExpect(jsonPath("$.payments[0].direction").value("OUT"))
                .andExpect(jsonPath("$.payments[1].paymentId").value("PAY-2"))
                .andExpect(jsonPath("$.payments[1].direction").value("IN"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.numberOfElements").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldReturnBadRequestWhenPageIsNegative() throws Exception {
        // When/Then
        mockMvc.perform(get("/payments/history/ACC-001")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenAccountNotFoundInHistory() throws Exception {
        // Given
        when(paymentService.getPaymentHistory(eq("NON-EXISTENT"), any()))
                .thenThrow(new AccountNotFoundException("Account not found: NON-EXISTENT"));

        // When/Then
        mockMvc.perform(get("/payments/history/NON-EXISTENT")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account not found: NON-EXISTENT"));
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsZero() throws Exception {
        // When/Then
        mockMvc.perform(get("/payments/history/ACC-001")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenSizeExceedsMax() throws Exception {
        // When/Then
        mockMvc.perform(get("/payments/history/ACC-001")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}

