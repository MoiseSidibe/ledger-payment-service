package com.alpian.ledger.payment.cucumber.steps;

import com.alpian.ledger.payment.cucumber.CucumberContext;
import com.alpian.ledger.payment.domain.EventStatus;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventEntity;
import com.alpian.ledger.payment.infrastructure.persistence.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RequiredArgsConstructor
public class OutboxSteps {

    private final CucumberContext context;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Then("an outbox event should be published with payload:")
    public void anOutboxEventShouldBePublishedWithPayload(DataTable dataTable) {
        Map<String, String> expectedPayload = dataTable.asMap();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    String paymentId = context.getLastCreatedPaymentId();
                    log.info("Polling for outbox event with payment ID: {}", paymentId);

                    assertThat(paymentId)
                            .withFailMessage("Payment ID is null - payment creation may have failed")
                            .isNotNull();

                    List<OutboxEventEntity> events = outboxEventRepository.findByAggregateId(paymentId);

                    assertThat(events)
                            .withFailMessage("No outbox events found for payment ID: " + paymentId)
                            .isNotEmpty();

                    OutboxEventEntity event = events.getFirst();

                    assertThat(event.getStatus()).isEqualTo(EventStatus.SENT);

                    JsonNode payload = objectMapper.readTree(event.getPayload());

                    assertThat(payload.get("type").asText())
                            .isEqualTo(expectedPayload.get("type"));
                    assertThat(payload.get("status").asText())
                            .isEqualTo(expectedPayload.get("status"));

                    String expectedFrom = expectedPayload.get("fromAccountId");
                    if (!"null".equals(expectedFrom) && expectedFrom != null) {
                        assertThat(payload.get("fromAccountId").asText()).isEqualTo(expectedFrom);
                    }

                    String expectedTo = expectedPayload.get("toAccountId");
                    if (!"null".equals(expectedTo) && expectedTo != null) {
                        assertThat(payload.get("toAccountId").asText()).isEqualTo(expectedTo);
                    }

                    BigDecimal expectedAmount = new BigDecimal(expectedPayload.get("amount"));
                    BigDecimal actualAmount = new BigDecimal(payload.get("amount").asText());
                    assertThat(actualAmount).isEqualByComparingTo(expectedAmount);

                    log.info("Verified outbox event {} published with status SENT", event.getEventId());
                });
    }

    @Then("no outbox event should be created")
    public void noOutboxEventShouldBeCreated() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String paymentId = context.getLastCreatedPaymentId();

        if (paymentId != null) {
            List<OutboxEventEntity> events = outboxEventRepository.findByAggregateId(paymentId);
            assertThat(events)
                    .withFailMessage("Expected no outbox events for payment ID: " + paymentId)
                    .isEmpty();
            log.info("Verified no outbox event created for payment ID: {}", paymentId);
        } else {
            List<OutboxEventEntity> allNewEvents = outboxEventRepository.findByStatus(EventStatus.NEW);
            assertThat(allNewEvents)
                    .withFailMessage("Expected no NEW outbox events in the system")
                    .isEmpty();
            log.info("Verified no NEW outbox events in the system");
        }
    }

    @Then("{int} outbox events should be published with SENT status")
    public void outboxEventsShouldBePublishedWithSentStatus(int expectedCount) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    List<OutboxEventEntity> sentEvents = outboxEventRepository.findByStatus(EventStatus.SENT);

                    log.info("Polling outbox events: {} SENT events found, expecting {}", sentEvents.size(), expectedCount);

                    assertThat(sentEvents.size())
                            .withFailMessage("Expected %d SENT outbox events but found %d", expectedCount, sentEvents.size())
                            .isGreaterThanOrEqualTo(expectedCount);
                });

        log.info("Verified {} outbox events published with SENT status", expectedCount);
    }
}

