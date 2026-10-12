package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import java.util.Comparator;
import java.util.List;

/** Records belong to dispatch; the installation footprint is the strike target. */
public record ColdAccountLayout(int evidenceX, int evidenceY, int withdrawalX, int withdrawalY,
                                float strikeX, float strikeY, float strikeRadius) {
    public static final float WITHDRAWAL_RADIUS = 12f;
    public static final float BLAST_MARGIN = 6f;
    public static ColdAccountLayout select(MapResult map, List<LandingPad> pads) {
        LandingPad exit = pads.get(0);
        var places = map.pointsOfInterest.stream()
                .filter(p -> p.kind != PointOfInterest.Kind.RESIDENTIAL && p.kind != PointOfInterest.Kind.LANDING_SITE)
                .filter(p -> map.grid.isWalkable(p.interiorAnchorX, p.interiorAnchorY))
                .filter(p -> GridPathfinder.findPath(map.grid, exit.centerX, exit.centerY,
                        p.interiorAnchorX, p.interiorAnchorY).length > 0).toList();
        PointOfInterest records = places.stream()
                .filter(p -> p.kind == PointOfInterest.Kind.ADMINISTRATIVE)
                .min(Comparator.comparingDouble(p -> distance(p, exit)))
                .orElseThrow(() -> new IllegalStateException("Cold Account needs an accessible dispatch office"));
        int left = places.stream().mapToInt(p -> p.left).min().orElseThrow();
        int right = places.stream().mapToInt(p -> p.right).max().orElseThrow();
        int top = places.stream().mapToInt(p -> p.top).min().orElseThrow();
        int bottom = places.stream().mapToInt(p -> p.bottom).max().orElseThrow();
        float radius = (float) Math.hypot(right - left + 1, bottom - top + 1) / 2f + 1f;
        float x = (left + right + 1) / 2f;
        float y = (top + bottom + 1) / 2f;
        if (Math.hypot(x - exit.centerX - .5f, y - exit.centerY - .5f)
                <= radius + BLAST_MARGIN + WITHDRAWAL_RADIUS)
            throw new IllegalStateException("Cold Account withdrawal must lie outside the bombardment exclusion area");
        return new ColdAccountLayout(records.interiorAnchorX, records.interiorAnchorY,
                exit.centerX, exit.centerY, x, y, radius);
    }
    private static double distance(PointOfInterest p, LandingPad exit) {
        return Math.hypot(p.interiorAnchorX - exit.centerX, p.interiorAnchorY - exit.centerY);
    }
}
