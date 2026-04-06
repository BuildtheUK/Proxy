package org.btuk.proxy.database.dto;

public record AutoModFlagDTO(String ruleId, long timestamp, String message, String messageWord, String flaggedWord) {
}
