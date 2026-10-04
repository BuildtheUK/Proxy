package org.btuk.proxy.database.dto;

public record StripeCustomerDTO(
    String customerId,
    String minecraftUuid,
    String minecraftName,
    long createdAt,
    long updatedAt
) {}
