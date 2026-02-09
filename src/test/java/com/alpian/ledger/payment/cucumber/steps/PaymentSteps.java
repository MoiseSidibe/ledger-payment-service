package com.alpian.ledger.payment.cucumber.steps;

import com.alpian.ledger.payment.api.dto.CreatePaymentRequest;
import com.alpian.ledger.payment.api.dto.PaymentResponse;
import com.alpian.ledger.payment.cucumber.CucumberContext;
import com.alpian.ledger.payment.cucumber.CucumberSpringConfiguration;
import com.alpian.ledger.payment.domain.PaymentStatus;
import com.alpian.ledger.payment.domain.TransactionType;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RequiredArgsConstructor
public class PaymentSteps {

    private final CucumberSpringConfiguration config;
    private final CucumberContext context;
    private final RestTemplate restTemplate = new RestTemplate();

    @When("I create a {string} payment with the following details:")
    public void iCreateAPaymentWithTheFollowingDetails(String transactionType, DataTable dataTable) {
        Map<String, String> data = dataTable.asMap();

        CreatePaymentRequest request = buildPaymentRequest(
                TransactionType.valueOf(transactionType),
                data.get("fromAccountId"),
                data.get("toAccountId"),
                data.get("amount")
        );

        UUID idempotencyKey = UUID.randomUUID();
        context.setLastIdempotencyKey(idempotencyKey);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", idempotencyKey.toString());

            HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                    config.getBaseUrl() + "/payments",
                    HttpMethod.POST,
                    entity,
                    PaymentResponse.class
            );

            context.setLastPaymentResponse(response);

            if (response.getBody() != null && response.getBody().paymentId() != null) {
                context.setLastCreatedPaymentId(response.getBody().paymentId());
                log.info("Payment created successfully: {}", response.getBody().paymentId());
            } else {
                log.error("Payment response body or payment ID is null. Response: {}", response);
                throw new IllegalStateException("Payment creation response body or payment ID is null");
            }

        } catch (HttpClientErrorException e) {
            context.setLastException(e);
            context.setLastErrorCode(extractErrorCode(e.getResponseBodyAsString()));
            context.setLastErrorMessage(e.getResponseBodyAsString());
            log.error("Payment failed: {}", e.getResponseBodyAsString());
        }
    }

    @When("I submit {int} identical {string} payment requests simultaneously with idempotency key {string}:")
    public void iSubmitIdenticalPaymentRequestsSimultaneously(int count, String transactionType, String idempotencyKey, DataTable dataTable) throws InterruptedException {
        Map<String, String> data = dataTable.asMap();

        CreatePaymentRequest request = buildPaymentRequest(
                TransactionType.valueOf(transactionType),
                data.get("fromAccountId"),
                data.get("toAccountId"),
                data.get("amount")
        );

        UUID key = UUID.fromString(idempotencyKey);
        context.setLastIdempotencyKey(key);

        try (ExecutorService executor = Executors.newFixedThreadPool(count)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(count);

            List<Future<ResponseEntity<PaymentResponse>>> futures = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for signal to start simultaneously

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("Idempotency-Key", key.toString());

                        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);

                        return restTemplate.exchange(
                                config.getBaseUrl() + "/payments",
                                HttpMethod.POST,
                                entity,
                                PaymentResponse.class
                        );
                    } catch (Exception e) {
                        log.debug("Request failed: {}", e.getMessage());
                        return null;
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            // Start all requests simultaneously
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            int successful = 0;
            int failed = 0;

            for (Future<ResponseEntity<PaymentResponse>> future : futures) {
                try {
                    ResponseEntity<PaymentResponse> response = future.get();
                    if (response != null && response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                        successful++;
                        context.setLastPaymentResponse(response);
                        context.setLastCreatedPaymentId(response.getBody().paymentId());
                        log.info("Concurrent payment succeeded: {}", response.getBody().paymentId());
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }

            context.setSuccessfulPayments(successful);
            context.setFailedPayments(failed);

            log.info("Concurrent requests: {} successful, {} failed", successful, failed);
        }
    }

    @When("I prepare {int} payment requests with the following details:")
    public void iPreparePaymentRequestsWithTheFollowingDetails(int count, DataTable dataTable) {
        Map<String, String> data = dataTable.asMap();

        CreatePaymentRequest request = buildPaymentRequest(
                TransactionType.valueOf(data.get("transactionType")),
                data.get("fromAccountId"),
                data.get("toAccountId"),
                data.get("amount")
        );

        if (context.getPreparedRequests() == null) {
            context.setPreparedRequests(new ArrayList<>());
        }

        for (int i = 0; i < count; i++) {
            context.getPreparedRequests().add(request);
            context.getPreparedIdempotencyKeys().add(UUID.randomUUID());
        }

        log.info("Prepared {} payment requests of type {}", count, data.get("transactionType"));
    }

    @When("I submit all payment requests simultaneously")
    public void iSubmitAllPaymentRequestsSimultaneously() throws InterruptedException {
        List<CreatePaymentRequest> requests = context.getPreparedRequests();
        List<UUID> keys = context.getPreparedIdempotencyKeys();

        if (requests == null || requests.isEmpty()) {
            throw new IllegalStateException("No prepared requests to submit");
        }

        int count = requests.size();

        try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(count, 50))) { // Limit threads
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(count);

            List<Future<ResponseEntity<PaymentResponse>>> futures = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                final CreatePaymentRequest request = requests.get(i);
                final UUID key = keys.get(i);

                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for signal to start simultaneously

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("Idempotency-Key", key.toString());

                        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);

                        return restTemplate.exchange(
                                config.getBaseUrl() + "/payments",
                                HttpMethod.POST,
                                entity,
                                PaymentResponse.class
                        );
                    } catch (Exception e) {
                        log.debug("Request failed: {}", e.getMessage());
                        return null;
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            // Start all requests simultaneously
            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

            if (!completed) {
                log.warn("Some requests did not complete within 30 seconds");
            }

            // Collect all responses
            List<ResponseEntity<PaymentResponse>> responses = new ArrayList<>();
            int successful = 0;
            int failed = 0;

            for (Future<ResponseEntity<PaymentResponse>> future : futures) {
                try {
                    ResponseEntity<PaymentResponse> response = future.get(1, TimeUnit.SECONDS);
                    if (response != null && response.getStatusCode() == HttpStatus.CREATED) {
                        successful++;
                        responses.add(response);
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.debug("Request failed: {}", e.getMessage());
                }
            }

            context.setSuccessfulPayments(successful);
            context.setFailedPayments(failed);
            context.setLastBatchResponses(responses);

            context.setPreparedRequests(new ArrayList<>());
            context.setPreparedIdempotencyKeys(new ArrayList<>());

            log.info("Submitted {} requests: {} successful, {} failed", count, successful, failed);
        }
    }

    @Then("the payment should be created successfully")
    public void thePaymentShouldBeCreatedSuccessfully() {
        assertThat(context.getLastPaymentResponse()).isNotNull();
        assertThat(context.getLastPaymentResponse().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(context.getLastCreatedPaymentId()).isNotNull();
        assertThat(context.getLastPaymentResponse().getBody()).isNotNull();
        assertThat(context.getLastPaymentResponse().getBody().status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Then("exactly {int} payment should succeed")
    public void exactlyPaymentShouldSucceed(int expectedSuccess) {
        assertThat(context.getSuccessfulPayments()).isEqualTo(expectedSuccess);
    }

    @Then("{int} payments should fail")
    public void paymentsShouldFailWith(int expectedFailures) {
        assertThat(context.getFailedPayments()).isEqualTo(expectedFailures);
    }

    @Then("{int} payment requests should succeed with COMPLETED status")
    public void paymentRequestsShouldSucceedWithCompletedStatus(int expectedCount) {
        assertThat(context.getSuccessfulPayments())
                .withFailMessage("Expected %d successful payments but got %d", expectedCount, context.getSuccessfulPayments())
                .isEqualTo(expectedCount);

        if (context.getLastBatchResponses() != null) {
            context.getLastBatchResponses().forEach(response -> {
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().status()).isEqualTo(PaymentStatus.COMPLETED);
            });
        }
    }

    @Then("the payment should fail with error {string}")
    public void thePaymentShouldFailWithError(String expectedErrorCode) {
        assertThat(context.getLastException()).isNotNull();
        assertThat(context.getLastErrorCode()).isEqualTo(expectedErrorCode);
    }

    @Then("the error message should contain {string}")
    public void theErrorMessageShouldContain(String expectedMessage) {
        assertThat(context.getLastErrorMessage()).containsIgnoringCase(expectedMessage);
    }

    private CreatePaymentRequest buildPaymentRequest(TransactionType type, String fromAccountId,
                                                     String toAccountId, String amount) {
        return new CreatePaymentRequest(
                type,
                "null".equals(fromAccountId) ? null : fromAccountId,
                "null".equals(toAccountId) ? null : toAccountId,
                new BigDecimal(amount)
        );
    }

    private String extractErrorCode(String responseBody) {
        if (responseBody.contains("\"error\"")) {
            int start = responseBody.indexOf("\"error\":\"") + 9;
            int end = responseBody.indexOf("\"", start);
            return responseBody.substring(start, end);
        }
        return "UNKNOWN";
    }
}

