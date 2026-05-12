package com.example.otel.model;

import java.time.Instant;
import java.util.UUID;

public record Order(
        String  id,
        String  customerId,
        String  product,
        double  amount,
        String  status,
        Instant createdAt
) {
    public static Order create(String customerId, String product, double amount) {
        return new Order(
                UUID.randomUUID().toString(),
                customerId,
                product,
                amount,
                "CREATED",
                Instant.now()
        );
    }

    public Order withStatus(String newStatus) {
        return new Order(id, customerId, product, amount, newStatus, createdAt);
    }
}
