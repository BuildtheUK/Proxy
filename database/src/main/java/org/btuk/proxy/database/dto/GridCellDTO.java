package org.btuk.proxy.database.dto;

public record GridCellDTO(
        double lat,
        double lon,
        double minLat,
        double maxLat,
        double minLon,
        double maxLon,
        int row,
        int col,
        int count
) {}
