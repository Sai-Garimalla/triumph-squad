package com.forge.orders.service;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class PaymentService {

    private final Random random = new Random();

    public enum PaymentResult {
        SUCCESS,
        TRANSIENT_TIMEOUT,
        PAYMENT_REJECTED
    }

    /**
     * Simulates payment gateway interaction.
     * Can simulate realistic network delay and occasional timeout for retry verification.
     */
    public PaymentResult processPayment(String orderNumber, double amount, int attemptNumber, boolean forceFailure) {
        if (forceFailure) {
            return PaymentResult.PAYMENT_REJECTED;
        }

        // Small processing delay simulating payment gateway roundtrip
        try {
            Thread.sleep(60 + random.nextInt(40));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // On first attempt, ~10% chance of transient network timeout to demonstrate retry mechanism
        if (attemptNumber == 1 && random.nextInt(100) < 10) {
            return PaymentResult.TRANSIENT_TIMEOUT;
        }

        return PaymentResult.SUCCESS;
    }
}
