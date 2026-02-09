package com.alpian.ledger.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LedgerPaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerPaymentServiceApplication.class, args);
	}

}
