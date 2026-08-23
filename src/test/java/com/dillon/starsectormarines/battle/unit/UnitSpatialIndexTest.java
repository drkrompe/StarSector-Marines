package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    public void factionGatherRejectsNearbyUnitsBeforeReturningCandidates() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long marine = roster.spawn(unit("marine", 10, 10));
        long defender = roster.spawn(new EntitySpec("defender", Faction.DEFENDER,
                UnitType.MILITIA, 11, 10));

        LongBucket out = new LongBucket();
        index.gatherFaction(10.5f, 10.5f, 4f, Faction.MARINE, out);

        assertTrue(contains(out, marine));
        assertFalse(contains(out, defender));
    }

    @Test
    public void nearestFactionCrossesEmptyBucketRingsAndBreaksTiesById() {
        UnitSpatialIndex index = new UnitSpatialIndex(96, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        roster.spawn(new EntitySpec("near defender", Faction.DEFENDER,
                UnitType.MILITIA, 8, 8));
        long firstMarine = roster.spawn(unit("first marine", 48, 8));
        roster.spawn(unit("far marine", 80, 8));
        long tiedLaterMarine = roster.spawn(unit("tied later marine", 48, 8));

        assertEquals(firstMarine,
                index.nearestFaction(8.5f, 8.5f, Faction.MARINE));
        assertTrue(firstMarine < tiedLaterMarine);
        assertEquals(tiedLaterMarine,
                index.nearestFaction(8.5f, 8.5f, Faction.MARINE,
                        candidate -> candidate != firstMarine));
        assertEquals(0L,
                index.nearestFaction(8.5f, 8.5f, Faction.CIVILIAN));
    }

    @Test
    public void snapshotQueriesRejectDirectlyReleasedUnits() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long released = roster.spawn(unit("released", 10, 10));

        roster.releaseFromRegistry(released);

        LongBucket out = new LongBucket();
        index.gatherFaction(10.5f, 10.5f, 4f, Faction.MARINE, out);
        assertFalse(contains(out, released));
        assertEquals(0L,
                index.nearestFaction(10.5f, 10.5f, Faction.MARINE));
    }

    @Test
    public void nearestFactionVisitsBoundaryDistanceForLowerIdTie() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long lowerIdAcrossBoundary = roster.spawn(unit("lower", 32, 8));
        long higherIdInCenterBucket = roster.spawn(unit("higher", 16, 8));
        roster.world().setPos(lowerIdAcrossBoundary, 32f, 8f);
        roster.world().setPos(higherIdInCenterBucket, 16f, 8f);
        index.rebuild(roster);

        assertTrue(lowerIdAcrossBoundary < higherIdInCenterBucket);
        assertEquals(lowerIdAcrossBoundary,
                index.nearestFaction(24f, 8f, Faction.MARINE));
    }

    private static boolean contains(LongBucket bucket, long id) {
        for (int i = 0; i < bucket.size; i++) {
            if (bucket.ids[i] == id) return true;
        }
        return false;
    }
}
