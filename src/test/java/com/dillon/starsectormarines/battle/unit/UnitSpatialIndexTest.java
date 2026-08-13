package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link UnitSpatialIndex#gatherAlongSegment}: it must surface a
 * unit sitting near the middle of a long ray — the case an endpoint-anchored
 * {@link UnitSpatialIndex#gather} radius query would miss entirely — while
 * still respecting the {@code margin} cutoff for a unit off to the side of
 * the ray.
 */
public class UnitSpatialIndexTest {

    private static EntitySpec unit(String label, int cellX, int cellY) {
        return new EntitySpec(label, Faction.MARINE, UnitType.MARINE_BLUE, cellX, cellY);
    }

    @Test
    public void gathersAUnitNearTheMidpointOfALongDiagonalRay() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        // Spawned at its cell center (30.5, 30.5) — sits exactly on the ray
        // below, roughly 40 cells from either endpoint.
        long midId = roster.spawn(unit("mid", 30, 30));

        LongBucket out = new LongBucket();
        index.gatherAlongSegment(2.5f, 2.5f, 58.5f, 58.5f, 1.0f, out);

        assertTrue(contains(out, midId), "expected the midpoint unit to be gathered");
    }

    @Test
    public void skipsAUnitFartherThanMarginFromTheRay() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        // Spawned at (27.5, 34.5) — about 4.95 cells of perpendicular offset
        // from the same diagonal ray near its midpoint, outside the 1.0-cell
        // margin.
        long offId = roster.spawn(unit("off", 27, 34));

        LongBucket out = new LongBucket();
        index.gatherAlongSegment(2.5f, 2.5f, 58.5f, 58.5f, 1.0f, out);

        assertFalse(contains(out, offId), "expected the off-ray unit to be excluded");
    }

    private static boolean contains(LongBucket bucket, long id) {
        for (int i = 0; i < bucket.size; i++) {
            if (bucket.ids[i] == id) return true;
        }
        return false;
    }
}
