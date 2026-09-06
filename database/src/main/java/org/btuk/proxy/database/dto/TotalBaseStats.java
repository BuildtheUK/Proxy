package org.btuk.proxy.database.dto;

public record TotalBaseStats(
        int buildings,
        int recentBuildings,
        int previousRecentBuildings
) {}