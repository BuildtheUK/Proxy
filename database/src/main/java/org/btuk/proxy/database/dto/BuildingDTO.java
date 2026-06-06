package org.btuk.proxy.database.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record BuildingDTO(int buildingId, int coordinateId, String playerId, boolean isPublic, boolean playerBuilt, LocalDateTime timeAdded, double lat, double lon) {
}
