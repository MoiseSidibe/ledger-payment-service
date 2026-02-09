package com.alpian.ledger.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.UUID;

/**
 * Swagger/OpenAPI configuration
 * Includes auto-generation of Idempotency-Key header for local testing
 */
@Configuration
public class SwaggerConfig {

    @Value("${spring.application.name:ledger-payment-service}")
    private String applicationName;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .version("1.0.0")
                        .description("Ledger Payment Service API - Handles payment transactions with idempotency support"));
    }

    /**
     * Auto-generates Idempotency-Key header with UUID examples for local testing
     * Only active in local/dev profiles to avoid confusion in production
     */
    @Bean
    @Profile({"default", "local", "dev"})
    public OperationCustomizer idempotencyKeyCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            Parameter[] parameters = handlerMethod.getMethod().getParameters();
            boolean hasIdempotencyKeyParam = Arrays.stream(parameters)
                    .anyMatch(param -> {
                        RequestHeader annotation = param.getAnnotation(RequestHeader.class);
                        return annotation != null &&
                               ("Idempotency-Key".equals(annotation.value()) ||
                                "Idempotency-Key".equals(annotation.name()));
                    });

            if (hasIdempotencyKeyParam) {
                if (operation.getParameters() != null) {
                    operation.getParameters().forEach(param -> {
                        if ("Idempotency-Key".equals(param.getName()) && "header".equals(param.getIn())) {
                            // Add a UUID example for easy testing
                            param.setExample(UUID.randomUUID().toString());
                            param.setDescription("Unique idempotency key for request deduplication. A new UUID is pre-filled for your convenience.");
                        }
                    });
                }
            }

            return operation;
        };
    }
}

