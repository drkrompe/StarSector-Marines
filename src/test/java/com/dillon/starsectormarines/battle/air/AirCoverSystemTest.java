package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Air cover flown in from off the map.
 *
 * <p>The committed wings had a schedule, a briefing row and a force-budget cost
 * and flew in a cosmetic overlay that could not be shot down, could not be
 * stopped by a roof, and was invisible to everything in the simulation. These
 * pin the sortie the air model flies instead — and, above all, that it comes
 * from off the map rather than being conjured onto it.
 */
class AirCoverSystemTest {

    private static final int W = 60;
    private static final int H = 40;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static void mass(BattleSimulation sim, Faction side, int x, int y, int count) {
        UnitType type = side == Faction.MARINE ? UnitType.MARINE : UnitType.MILITIA;
        for (int i = 0; i < count; i++) {
            sim.spawn(new EntitySpec(side + "-" + x + "-" + i, side, type,
                    x + (i % 3), y + (i / 3)));
        }
    }

    private static ShuttleMission coverSortie(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = sim.world().mission(id);
            if (m != null && m.strikeSortie && m.homeBerth == null) return m;
        }
        return null;
    }

    private static void advance(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    /** A committed wing turns up, and it turns up from outside the map. */
    @Test
    void aCommittedWingFliesInFromOffTheMap() {
        BattleSimulation sim = openSim();
        mass(sim, Faction.MARINE, 6, 20, 4);
        mass(sim, Faction.DEFENDER, 45, 20, 6);
        sim.setFlybyRoster(new FlybyRoster(List.of(
                FighterWing.single(FighterProfile.BROADSWORD, Faction.MARINE, 2f))));

        advance(sim, 5 * 30);

        ShuttleMission cover = coverSortie(sim);
        assertNotNull(cover, "the committed wing never flew");
        assertFalse(cover.usesRunway, "air cover claimed a runway it has no business on");
        assertNull(cover.homeBerth, "air cover took a berth off a field it does not belong to");
        boolean offMap = cover.entryX < 0f || cover.entryX > W
                || cover.entryY < 0f || cover.entryY > H;
        assertTrue(offMap, "the sortie was conjured on the map at ("
                + cover.entryX + "," + cover.entryY + ")");
        assertTrue(cover.lzX > 35f, "sent somewhere other than the concentration");
    }

    /** It comes in over its own people, not over the enemy's. */
    @Test
    void itCrossesTheEdgeItsOwnForceIsNearest() {
        BattleSimulation sim = openSim();
        mass(sim, Faction.MARINE, 3, 20, 4);
        mass(sim, Faction.DEFENDER, 50, 20, 6);
        sim.setFlybyRoster(new FlybyRoster(List.of(
                FighterWing.single(FighterProfile.BROADSWORD, Faction.MARINE, 2f))));

        advance(sim, 5 * 30);

        ShuttleMission cover = coverSortie(sim);
        assertNotNull(cover);
        assertTrue(cover.entryX < 0f, "entered over the enemy at x=" + cover.entryX);
        assertTrue(cover.exitX < 0f, "left over the enemy at x=" + cover.exitX);
    }

    /**
     * A scattered enemy is not worth a sortie, and the wing is not spent on it
     * — the schedule slips rather than the aircraft being thrown away.
     */
    @Test
    void aWingWithNothingWorthAttackingDoesNotFly() {
        BattleSimulation sim = openSim();
        mass(sim, Faction.MARINE, 6, 20, 4);
        for (int i = 0; i < 6; i++) {
            sim.spawn(new EntitySpec("d" + i, Faction.DEFENDER,
                    UnitType.MILITIA, 4 + i * 9, 35));
        }
        sim.setFlybyRoster(new FlybyRoster(List.of(
                FighterWing.single(FighterProfile.BROADSWORD, Faction.MARINE, 2f))));

        advance(sim, 20 * 30);

        assertNull(coverSortie(sim), "flew a sortie at nothing in particular");
    }

    /** A wing flies the sorties it was committed for and no more. */
    @Test
    void aWingFliesOnlyItsScheduledSorties() {
        BattleSimulation sim = openSim();
        mass(sim, Faction.MARINE, 6, 20, 4);
        mass(sim, Faction.DEFENDER, 45, 20, 6);
        sim.setFlybyRoster(new FlybyRoster(List.of(
                new FighterWing(FighterProfile.BROADSWORD, Faction.MARINE, 2, 2f, 10f))));

        // Watched across the window: a sortie finishes and leaves, so the
        // instant a test happens to look says nothing about how many flew.
        Set<Long> seen = new HashSet<>();
        for (int t = 0; t < 180 * 30; t++) {
            sim.advance(BattleSimulation.TICK_DT);
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission m = sim.world().mission(id);
                if (m != null && m.strikeSortie && m.homeBerth == null) seen.add(id);
            }
        }
        assertEquals(2, seen.size(), "the wing flew " + seen.size() + " sorties, not its two");
    }
}
