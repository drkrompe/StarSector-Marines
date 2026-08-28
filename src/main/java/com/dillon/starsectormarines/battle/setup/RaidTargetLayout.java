package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic high-value target and return corridor for a Raid battle. */
public record RaidTargetLayout(
        String targetId,
        String targetName,
        PointOfInterest.Kind kind,
        int targetCellX,
        int targetCellY,
        int egressCellX,
        int egressCellY) {

    private record Candidate(PointOfInterest poi, int routeLength,
                             int distanceFromEgressSquared) { }

    public RaidTargetLayout {
        if (targetId == null || targetId.isBlank()
                || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("stable Raid target identity required");
        }
        Objects.requireNonNull(kind, "kind");
    }

    public static RaidTargetLayout select(MapResult map,
                                          List<LandingPad> landingPads) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(landingPads, "landingPads");
        if (landingPads.isEmpty()) {
            throw new IllegalArgumentException("Raid requires at least one landing pad");
        }
        LandingPad egress = landingPads.get(0);
        int defenderHalfX = map.grid.getWidth() / 2;
        Candidate selected = map.pointsOfInterest.stream()
                .filter(poi -> poi.kind != PointOfInterest.Kind.RESIDENTIAL)
                .filter(poi -> poi.interiorAnchorX >= defenderHalfX)
                .filter(poi -> map.grid.inBounds(poi.interiorAnchorX,
                        poi.interiorAnchorY))
                .filter(poi -> map.grid.isWalkable(poi.interiorAnchorX,
                        poi.interiorAnchorY))
                .map(poi -> candidate(map, egress, poi))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Candidate::distanceFromEgressSquared)
                        .thenComparingInt(candidate -> -candidate.routeLength())
                        .thenComparingInt(candidate -> -candidate.poi().interiorAnchorY)
                        .thenComparingInt(candidate -> -candidate.poi().interiorAnchorX))
                .orElseThrow(() -> new IllegalStateException(
                        "Raid map has no reachable high-value defender-half target"));
        PointOfInterest poi = selected.poi();
        String label = poi.kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return new RaidTargetLayout("RAID-01", label, poi.kind,
                poi.interiorAnchorX, poi.interiorAnchorY,
                egress.centerX, egress.centerY);
    }

    private static Candidate candidate(MapResult map, LandingPad egress,
                                       PointOfInterest poi) {
        int[] route = GridPathfinder.findPath(map.grid, egress.centerX,
                egress.centerY, poi.interiorAnchorX, poi.interiorAnchorY);
        if (route.length == 0) return null;
        int dx = poi.interiorAnchorX - egress.centerX;
        int dy = poi.interiorAnchorY - egress.centerY;
        return new Candidate(poi, route.length, dx * dx + dy * dy);
    }
}
