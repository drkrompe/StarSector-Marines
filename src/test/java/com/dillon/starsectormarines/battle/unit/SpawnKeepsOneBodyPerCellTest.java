package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawn seam's placement rule: an arrival that will never move gets the cell
 * it was given, and the arrival always happens.
 */
public class SpawnKeepsOneBodyPerCellTest {

    private static UnitRosterService roster(NavigationGrid grid) {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(64, 64), null);
        roster.setNavigationGrid(grid);
        return roster;
    }

    /** An open field, so a displaced body always has somewhere to go. */
    private static NavigationGrid openField() {
        NavigationGrid grid = new NavigationGrid(64, 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static long spawn(UnitRosterService roster, UnitType type, int x, int y) {
        return roster.spawn(new EntitySpec(type.name() + x + "-" + y,
                Faction.DEFENDER, type, x, y).hp(10f));
    }

    private static boolean sameCell(World world, long a, long b) {
        return world.cellX(a) == world.cellX(b) && world.cellY(a) == world.cellY(b);
    }

    @Test
    void aStructureTakesItsCellAndTheOccupantStepsOff() {
        UnitRosterService roster = roster(openField());
        World world = roster.world();
        long standing = spawn(roster, UnitType.MARINE, 20, 20);

        long airframe = spawn(roster, UnitType.BASED_AIRCRAFT, 20, 20);

        assertEquals(20, world.cellX(airframe), "a hardstand is the one place its aircraft can be");
        assertEquals(20, world.cellY(airframe));
        assertFalse(sameCell(world, standing, airframe),
                "a hull whose placement is final may not be stood on top of somebody");
        assertTrue(Math.max(Math.abs(world.cellX(standing) - 20),
                        Math.abs(world.cellY(standing) - 20)) <= 3,
                "a step off the stand, not a relocation");
    }

    @Test
    void aBodyOnThePadCannotStopTheFieldPlacingAnAircraft() {
        UnitRosterService roster = roster(openField());
        int before = roster.liveCount();

        spawn(roster, UnitType.MARINE, 20, 20);
        long airframe = spawn(roster, UnitType.BASED_AIRCRAFT, 20, 20);

        assertNotEquals(0L, airframe, "refusing the spawn would make a pad a denial vector");
        assertEquals(before + 2, roster.liveCount(), "both bodies are on the map");
    }

    @Test
    void aStructureStandsWhereItWasAskedWhenThereIsNowhereToStepTo() {
        NavigationGrid walledIn = new NavigationGrid(64, 64);
        walledIn.setWalkableFloor(20, 20);
        UnitRosterService roster = roster(walledIn);
        World world = roster.world();
        long held = spawn(roster, UnitType.MARINE, 20, 20);

        long airframe = spawn(roster, UnitType.BASED_AIRCRAFT, 20, 20);

        assertTrue(sameCell(world, held, airframe),
                "an overlap is worse than a missing aircraft, so the spawn still happens");
    }

    /**
     * Two bodies that can both walk are left exactly where they were asked for.
     *
     * <p>Their overlap is {@code SeparationSystem}'s, which resolves it every
     * tick and has to, because play produces overlaps no spawn check could have
     * prevented. Deciding it a second time here would move where several hundred
     * units start a battle and buy nothing.
     */
    @Test
    void twoMoversAreLeftToSeparation() {
        UnitRosterService roster = roster(openField());
        World world = roster.world();
        long held = spawn(roster, UnitType.MARINE, 20, 20);

        long arriving = spawn(roster, UnitType.MARINE, 20, 20);

        assertTrue(sameCell(world, held, arriving));
        assertEquals(20, world.cellX(arriving));
        assertEquals(20, world.cellY(arriving));
    }
}
