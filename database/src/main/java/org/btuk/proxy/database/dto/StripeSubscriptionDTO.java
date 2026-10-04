package org.btuk.proxy.database.dto;

public record StripeSubscriptionDTO(
    String subscriptionId,
    String customerId,
    String minecraftUuid,
    String status,
    String planId,
    int durationMonths,
    Long currentPeriodStart,
    Long currentPeriodEnd,
    long createdAt,
    long updatedAt
) {}
