package org.btuk.proxy.database.dto;

public record StripePaymentDTO(
    String id,
    String chargeId,
    String customerId,
    String subscriptionId,
    String minecraftUuid,
    String paymentType,
    long amount,
    String currency,
    int durationDays,
    String status,
    long createdAt,
    Long refundedAt
) {}
