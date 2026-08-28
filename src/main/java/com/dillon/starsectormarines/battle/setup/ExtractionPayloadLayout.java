package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic source, egress, and traversable corridor for generic Extraction. */
public record ExtractionPayloadLayout(
        String payloadId,
        String payloadName,
        PointOfInterest.Kind sourceKind,
        int sourceCellX,
        int sourceCellY,
        int egressCellX,
        int egressCellY,
        int[] route) {

    private record Candidate(PointOfInterest poi, int[] route,
                             int distanceSquared) { }

    public ExtractionPayloadLayout {
        if (payloadId == null || payloadId.isBlank()
                || payloadName == null || payloadName.isBlank()
                || route == null || route.length < 4 || (route.length & 1) != 0) {
            throw new IllegalArgumentException("stable reachable Extraction payload required");
        }
        Objects.requireNonNull(sourceKind, "sourceKind");
        route = Arrays.copyOf(route, route.length);
    }

    @Override public int[] route() { return Arrays.copyOf(route, route.length); }

    public static ExtractionPayloadLayout select(
            MapResult map, List<LandingPad> landingPads) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(landingPads, "landingPads");
        if (landingPads.isEmpty()) {
            throw new IllegalArgumentException("Extraction requires at least one landing pad");
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
                .max(Comparator.comparingInt(Candidate::distanceSquared)
                        .thenComparingInt(candidate -> candidate.route().length)
                        .thenComparingInt(candidate -> -candidate.poi().interiorAnchorY)
                        .thenComparingInt(candidate -> -candidate.poi().interiorAnchorX))
                .orElseThrow(() -> new IllegalStateException(
                        "Extraction map has no reachable defender-half payload source"));
        PointOfInterest source = selected.poi();
        String name = source.kind.name().toLowerCase(Locale.ROOT)
                .replace('_', ' ') + " recovery package";
        return new ExtractionPayloadLayout("EXTRACTION-01", name, source.kind,
                source.interiorAnchorX, source.interiorAnchorY,
                egress.centerX, egress.centerY, selected.route());
    }

    private static Candidate candidate(MapResult map, LandingPad egress,
                                       PointOfInterest poi) {
        int[] route = GridPathfinder.findPath(map.grid, poi.interiorAnchorX,
                poi.interiorAnchorY, egress.centerX, egress.centerY);
        if (route.length == 0) return null;
        int dx = poi.interiorAnchorX - egress.centerX;
        int dy = poi.interiorAnchorY - egress.centerY;
        return new Candidate(poi, route, dx * dx + dy * dy);
    }
}
