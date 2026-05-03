package org.btuk.proxy.api.impl;

import jakarta.ws.rs.core.Response;
import org.btuk.proxy.api.BuildingsApi;
import org.btuk.proxy.api.model.Building;
import org.btuk.proxy.database.dto.BuildingDTO;
import org.btuk.proxy.database.sql.GlobalSQL;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BuildingsApiImpl implements BuildingsApi {

    private final GlobalSQL globalSQL;

    public BuildingsApiImpl(GlobalSQL globalSQL) {
        this.globalSQL = globalSQL;
    }

    @Override
    public Response getBuildingsByArea(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        List<BuildingDTO> buildingDTOs = globalSQL.getBuildingsByArea(minLat, maxLat, minLon, maxLon);
        List<Building> buildings = buildingDTOs.stream()
                .map(this::mapBuilding)
                .collect(Collectors.toList());
        return Response.ok(buildings).build();
    }

    @Override
    public Response getBuildingsByPlayer(UUID uuid) {
        List<BuildingDTO> buildingDTOs = globalSQL.getBuildingsByPlayer(uuid.toString());
        List<Building> buildings = buildingDTOs.stream()
                .map(this::mapBuilding)
                .collect(Collectors.toList());
        return Response.ok(buildings).build();
    }

    private Building mapBuilding(BuildingDTO dto) {
        Building building = new Building();
        building.setBuildingId(dto.buildingId());
        building.setCoordinateId(dto.coordinateId());
        building.setPlayerId(dto.playerId() != null ? UUID.fromString(dto.playerId()) : null);
        building.setIsPublic(dto.isPublic());
        building.setPlayerBuilt(dto.playerBuilt());
        if (dto.timeAdded() != null) {
            building.setTimeAdded(Date.from(dto.timeAdded().toInstant(ZoneOffset.UTC)));
        }
        building.setLat(dto.lat());
        building.setLon(dto.lon());
        return building;
    }
}
