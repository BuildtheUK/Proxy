package org.btuk.proxy.database.dto;

public record StripeEventDTO(
    String id,
    String type,
    String status,
    String errorMessage,
    long createdAt,
    Long processedAt
) {}
