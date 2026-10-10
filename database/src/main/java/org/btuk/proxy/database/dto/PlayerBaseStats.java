package org.btuk.proxy.database.dto;

public record PlayerBaseStats(
        int buildings,
        int tplls,
        int timePlayed,
        int messagesSent
) {}