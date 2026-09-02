package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An allied force is in the player's picture without a mission having to say
 * so.
 *
 * <p>Fog-of-war law 2 makes contributor membership an explicit decision rather
 * than an inference from renderer ownership, and {@link PlayerVisionState}'s
 * default is where that decision is taken for allies. Leaving it to setup would
 * put it in a dozen creation paths, and a path that forgot would produce no
 * error at all — only a militia the player cannot see fighting on their side.
 *
 * <p>The scene is two sealed rooms and a marine outside both, so the ally
 * cannot see the defender for the ally, and the marine cannot see either.
 * What separates the two bodies is nothing but which faction they carry.
 */
class AlliedSightContributionTest {

    private static final int W = 40;
    private static final int H = 20;

    /** Two sealed rooms, symmetric about the marine, both out of ordinary sight. */
    private static final int ALLY_X = 6;
    private static final int DEFENDER_X = 33;
    private static final int ROOM_Y = 10;
    private static final int MARINE_X = 20;

    private static final int SETTLE_TICKS = 9;

    @Test
    void theDefaultContributorSetIsTheCompanyAndItsAllies() {
        PlayerVisionState state = new PlayerVisionState();

        assertTrue(state.isContributor(Faction.MARINE));
        assertTrue(state.isContributor(Faction.ALLY));
        assertFalse(state.isContributor(Faction.DEFENDER),
                "an enemy runs its own perception layer");
        assertFalse(state.isContributor(Faction.CIVILIAN));
    }

    @Test
    void anAlliedBodyIsInThePlayersPictureAndADefenderAtTheSameRemoveIsNot() {
        try (BattleSimulation sim = scene()) {
            long ally = spawn(sim, "ally", Faction.ALLY, ALLY_X);
            long defender = spawn(sim, "defender", Faction.DEFENDER, DEFENDER_X);
            spawn(sim, "marine", Faction.MARINE, MARINE_X);
            settle(sim);

            assertEquals(FogOfWarService.VIS_VISIBLE, visibilityOf(sim, ally),
                    "an ally is shown the way one of the player's own marines is, "
                            + "with no sweep and no line of sight to it");
            assertEquals(FogOfWarService.VIS_HIDDEN, visibilityOf(sim, defender),
                    "while a defender sealed in the mirror-image room stays dark");
        }
    }

    /**
     * The ally is a real contributor and not merely exempt from the fog gate:
     * the ground it stands on is revealed to the player, and nothing the
     * marine can see accounts for it.
     */
    @Test
    void anAlliedBodyRevealsTheGroundItStandsOn() {
        try (BattleSimulation sim = scene()) {
            spawn(sim, "marine", Faction.MARINE, MARINE_X);
            settle(sim);
            assertFalse(sim.getFogOfWar().isCellRevealed(ALLY_X, ROOM_Y),
                    "the sealed room is dark before anybody is in it");
        }
        try (BattleSimulation sim = scene()) {
            spawn(sim, "ally", Faction.ALLY, ALLY_X);
            spawn(sim, "marine", Faction.MARINE, MARINE_X);
            settle(sim);
            assertTrue(sim.getFogOfWar().isCellRevealed(ALLY_X, ROOM_Y),
                    "an allied contributor opens the room it is standing in");
        }
    }

    /** A mission that wants otherwise can still say so. */
    @Test
    void aMissionMayStillDropTheAllyBackOutOfTheContributorSet() {
        PlayerVisionState state = new PlayerVisionState();
        state.removeContributor(Faction.ALLY);
        assertFalse(state.isContributor(Faction.ALLY));
        assertTrue(state.isContributor(Faction.MARINE));
    }

    private static long spawn(BattleSimulation sim, String id, Faction faction, int x) {
        return sim.spawn(new EntitySpec(id, faction, UnitType.MARINE, x, ROOM_Y)
                .moveSpeed(0f));
    }

    private static void settle(BattleSimulation sim) {
        for (int i = 0; i < SETTLE_TICKS; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static byte visibilityOf(BattleSimulation sim, long unit) {
        UnitRosterService roster = sim.getRoster();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            if (roster.get(i) == unit) return sim.getFogOfWar().getUnitVisibility(i);
        }
        throw new AssertionError("unit is no longer live");
    }

    /**
     * Open ground with a sealed 3x3 room at either end. Nothing has a door, so
     * ordinary sight reaches neither interior from the middle and neither
     * interior reaches the other.
     */
    private static BattleSimulation scene() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        seal(grid, topology, ALLY_X);
        seal(grid, topology, DEFENDER_X);

        BattleSimulation sim = new BattleSimulation(grid, topology, 20260902L);
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** A ring of wall one cell out from ({@code centerX}, {@link #ROOM_Y}). */
    private static void seal(NavigationGrid grid, CellTopology topology, int centerX) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int x = centerX + dx;
                int y = ROOM_Y + dy;
                grid.setWalkable(x, y, false);
                topology.setWall(x, y, true);
            }
        }
    }
}
