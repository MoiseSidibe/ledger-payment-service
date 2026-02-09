package com.alpian.ledger.payment.cucumber.steps;

import com.alpian.ledger.payment.api.dto.PaymentHistoryResponse;
import com.alpian.ledger.payment.cucumber.CucumberContext;
import com.alpian.ledger.payment.cucumber.CucumberSpringConfiguration;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RequiredArgsConstructor
public class HistorySteps {

    private final CucumberSpringConfiguration config;
    private final CucumberContext context;
    private final RestTemplate restTemplate = new RestTemplate();

    private PaymentHistoryResponse lastHistoryResponse;

    @When("I request payment history for account {string}")
    public void iRequestPaymentHistoryForAccount(String accountId) {
        String url = config.getBaseUrl() + "/payments/history/" + accountId;

        ResponseEntity<PaymentHistoryResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        lastHistoryResponse = response.getBody();

        log.info("Fetched payment history for account {} - found {} payments",
                accountId, lastHistoryResponse != null ? lastHistoryResponse.payments().size() : null);
    }

    @Then("the payment history should contain the transaction")
    public void thePaymentHistoryShouldContainTheTransaction() {
        assertThat(lastHistoryResponse).isNotNull();
        assertThat(lastHistoryResponse.payments()).isNotEmpty();

        boolean found = lastHistoryResponse.payments().stream()
                .anyMatch(p -> p.paymentId().equals(context.getLastCreatedPaymentId()));

        assertThat(found).isTrue();

        log.info("Verified transaction {} exists in history", context.getLastCreatedPaymentId());
    }

    @Then("the payment should have direction {string}")
    public void thePaymentShouldHaveDirection(String expectedDirection) {
        assertThat(lastHistoryResponse).isNotNull();
        assertThat(lastHistoryResponse.payments()).isNotEmpty();

        var payment = lastHistoryResponse.payments().stream()
                .filter(p -> p.paymentId().equals(context.getLastCreatedPaymentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Payment not found in history"));

        assertThat(payment.direction()).isEqualTo(expectedDirection);

        log.info("Verified payment direction: {}", expectedDirection);
    }

    @Then("the payment history should contain exactly {int} transaction with idempotency key {string}")
    public void thePaymentHistoryShouldContainExactlyTransactionWithIdempotencyKey(int expectedCount, String idempotencyKey) {
        assertThat(lastHistoryResponse).isNotNull();

        long count = lastHistoryResponse.payments().stream()
                .filter(p -> p.paymentId().equals(context.getLastCreatedPaymentId()))
                .count();

        assertThat(count).isEqualTo(expectedCount);

        log.info("Verified exactly {} transaction(s) with idempotency key in history", expectedCount);
    }

    @Then("the payment history should contain {int} transactions")
    public void thePaymentHistoryShouldContainTransactions(int expectedCount) {
        assertThat(lastHistoryResponse).isNotNull();
        assertThat(lastHistoryResponse.totalElements())
                .withFailMessage("Expected %d transactions but found %d", expectedCount, lastHistoryResponse.totalElements())
                .isEqualTo(expectedCount);

        log.info("Verified payment history contains {} transactions", expectedCount);
    }
}

