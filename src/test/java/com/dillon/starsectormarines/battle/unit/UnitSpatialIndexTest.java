package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link UnitSpatialIndex#gatherAlongSegment}: it must surface a
 * unit sitting near the middle of a long ray — the case an endpoint-anchored
 * {@link UnitSpatialIndex#gather} radius query would miss entirely — while
 * still respecting the {@code margin} cutoff for a unit off to the side of
 * the ray.
 */
public class UnitSpatialIndexTest {

    @Test void pointGatherRetainsSnapshotCoordinatesOrderGrowthAndClearing() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long moved = roster.spawn(unit("moved", 14, 10));
        for (int i = 0; i < 35; i++) roster.spawn(unit("near" + i, 10, 10));
        roster.world().setPos(moved, 40.5f, 40.5f);
        LongBucket ids = new LongBucket();
        UnitSpatialIndex.PointBuffer points = new UnitSpatialIndex.PointBuffer();
        index.gather(10.5f, 10.5f, 4, ids);
        index.gather(10.5f, 10.5f, 4, points);
        assertEquals(36, points.size);
        for (int i = 0; i < ids.size; i++) assertEquals(ids.ids[i], points.ids[i]);
        assertEquals(moved, points.ids[0]);
        assertEquals(14.5f, points.x[0]);
        assertEquals(10.5f, points.y[0]);
        assertEquals(0, points.bucketX[0]);
        index.gather(10.5f, 10.5f, 0, points);
        assertEquals(0, points.size);
        index.gather(-1.5f, 10.5f, 1, points);
        assertEquals(0, points.size);
    }

    @Test void destinationPointsKeepLiveEndpointAndOldBucketUntilRebuild() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        UnitDestinationSpatialIndex destinations = new UnitDestinationSpatialIndex(64, 64);
        long id = roster.spawn(unit("moving", 3, 10));
        roster.world().setPathRef(id, new int[]{15, 10});
        destinations.rebuild(roster);
        roster.world().setPathRef(id, new int[]{18, 10});
        UnitSpatialIndex.PointBuffer points = new UnitSpatialIndex.PointBuffer();
        destinations.gather(roster, 16.5f, 10.5f, 6, points);
        assertEquals(1, points.size);
        assertEquals(id, points.ids[0]);
        assertEquals(18.5f, points.x[0]);
        assertEquals(0, points.bucketX[0]);
        LongBucket narrow = new LongBucket();
        destinations.gather(roster, 17.5f, 10.5f, 2, narrow);
        assertEquals(1, narrow.size);
        destinations.gather(roster, 18.5f, 10.5f, 2, narrow);
        assertEquals(0, narrow.size);
        roster.world().setPathRef(id, new int[0]);
        destinations.gather(roster, 16.5f, 10.5f, 6, points);
        assertEquals(0, points.size);
    }

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
    public void factionGatherRetainsFilteredDenseOrderInAMixedBucket() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long firstMarine = roster.spawn(unit("first marine", 10, 10));
        for (int i = 0; i < 80; i++) {
            roster.spawn(new EntitySpec("swarm-" + i, Faction.DEFENDER,
                    UnitType.SWARM_RUNNER, 10, 10));
        }
        long secondMarine = roster.spawn(unit("second marine", 10, 10));

        LongBucket out = new LongBucket();
        index.gatherFaction(10.5f, 10.5f, 4f, Faction.MARINE, out);

        assertEquals(2, out.size);
        assertEquals(firstMarine, out.ids[0]);
        assertEquals(secondMarine, out.ids[1]);
    }

    /**
     * The querier here is a defender, which is what makes the mixed bucket
     * worth building: it fights both the company and the militia beside it, so
     * the expected set spans two factions rather than "everybody else". Its own
     * side and a hostile faction's non-combatants are the two things filtered
     * out, and dense-roster order survives both a growth and a release.
     */
    @Test
    public void hostileCombatantQueriesPreserveBoundaryOrderGrowthAndRemoval() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        roster.spawn(new EntitySpec("own side", Faction.DEFENDER,
                UnitType.MILITIA, 10, 10));
        for (int i = 0; i < 80; i++) {
            roster.spawn(new EntitySpec("civilian-" + i, Faction.MARINE,
                    UnitType.CIVILIAN, 10, 10));
        }
        long[] expected = new long[21];
        for (int i = 0; i < 20; i++) {
            Faction faction = (i & 1) == 0
                    ? Faction.MARINE : Faction.ALLY;
            expected[i] = roster.spawn(new EntitySpec("combatant-" + i,
                    faction, UnitType.MILITIA, 10, 10));
        }
        expected[20] = roster.spawn(new EntitySpec("boundary",
                Faction.MARINE, UnitType.MILITIA, 14, 10));
        roster.spawn(new EntitySpec("outside", Faction.MARINE,
                UnitType.MILITIA, 15, 10));
        index.rebuild(roster);

        LongBucket out = new LongBucket();
        index.gatherHostileCombatants(10.5f, 10.5f, 4f,
                Faction.DEFENDER, out);

        assertEquals(expected.length, out.size);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], out.ids[i], "filtered order at " + i);
        }

        roster.releaseFromRegistry(expected[7]);
        index.gatherHostileCombatants(10.5f, 10.5f, 4f,
                Faction.DEFENDER, out);
        assertEquals(expected.length - 1, out.size);
        int actual = 0;
        for (int i = 0; i < expected.length; i++) {
            if (i == 7) continue;
            assertEquals(expected[i], out.ids[actual++],
                    "post-release order at " + i);
        }
        assertEquals(expected.length - 2,
                index.countHostileCombatants(10.5f, 10.5f, 4f,
                        Faction.DEFENDER, expected[0]));
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
        index.gatherAlongSegment(6.5f, 10.5f, 14.5f, 10.5f, 1f, out);
        assertFalse(contains(out, released));
    }

    @Test
    public void releasePreservesRemainingSnapshotOrder() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long first = roster.spawn(unit("first", 10, 10));
        long released = roster.spawn(unit("released", 10, 10));
        long last = roster.spawn(unit("last", 10, 10));

        roster.releaseFromRegistry(released);

        LongBucket out = new LongBucket();
        index.gather(10.5f, 10.5f, 4f, out);
        assertEquals(2, out.size);
        assertEquals(first, out.ids[0]);
        assertEquals(last, out.ids[1]);

        index.gatherFaction(10.5f, 10.5f, 4f, Faction.MARINE, out);
        assertEquals(2, out.size);
        assertEquals(first, out.ids[0]);
        assertEquals(last, out.ids[1]);

        roster.releaseFromRegistry(released);
        roster.releaseFromRegistry(Long.MAX_VALUE);
        index.gather(10.5f, 10.5f, 4f, out);
        assertEquals(2, out.size);
        assertEquals(first, out.ids[0]);
        assertEquals(last, out.ids[1]);
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

    @Test
    public void rebuildRetainsDenseOrderAcrossArchetypesAndScratchGrowth() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long released = 0L;
        for (int i = 0; i < 70; i++) {
            UnitType type = (i & 1) == 0
                    ? UnitType.MARINE_BLUE : UnitType.CIVILIAN;
            long id = roster.spawn(new EntitySpec("unit-" + i, Faction.MARINE,
                    type, 10 + (i & 1), 10));
            if (i == 7) released = id;
        }
        roster.releaseFromRegistry(released);

        index.rebuild(roster);

        LongBucket out = new LongBucket();
        index.gather(10.5f, 10.5f, 4f, out);
        assertEquals(roster.liveCount(), out.size);
        for (int i = 0; i < roster.liveCount(); i++) {
            assertEquals(roster.get(i), out.ids[i], "dense order at " + i);
        }
    }

    @Test
    public void rebuildDoesNotReuseStaleScratchForMalformedLiveUnit() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long unit = roster.spawn(unit("unit", 10, 10));
        index.rebuild(roster);

        roster.entityWorld().removeComponent(unit, roster.components().POSITION);

        assertThrows(IllegalStateException.class, () -> index.rebuild(roster));
    }

    private static boolean contains(LongBucket bucket, long id) {
        for (int i = 0; i < bucket.size; i++) {
            if (bucket.ids[i] == id) return true;
        }
        return false;
    }
}
