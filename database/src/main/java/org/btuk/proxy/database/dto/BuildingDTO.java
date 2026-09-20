package org.btuk.proxy.database.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record BuildingDTO(
        int buildingId,
        String playerId,
        String playerName, // Added field
        boolean isPublic,
        boolean playerBuilt,
        LocalDateTime timeAdded,
        double lat,
        double lon
) {}
