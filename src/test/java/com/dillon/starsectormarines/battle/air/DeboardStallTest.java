package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A delivery that cannot put anybody down leaves; it does not park.
 *
 * <p>Deboarding looks for a walkable, unoccupied cell near the LZ and retries
 * on the deboard interval when it finds none. With no bound on that retry, a
 * craft whose landing zone is full holds it for the rest of the battle with its
 * passengers still aboard — the reinforcement neither arrives nor is ever
 * reported lost, and the aircraft sits on the map looking delivered. It is
 * reachable without contrivance: a squad that lands and holds around its own
 * drop point is enough to fill the search.
 */
class DeboardStallTest {

    private static final int W = 24;
    private static final int H = 24;
    private static final int LZ_X = 12;
    private static final int LZ_Y = 12;

    /** A grid whose only standable ground is a sealed pocket around the LZ. */
    private static BattleSimulation pocketSim(int pocketRadius) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = LZ_Y - pocketRadius; y <= LZ_Y + pocketRadius; y++) {
            for (int x = LZ_X - pocketRadius; x <= LZ_X + pocketRadius; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** Stands a unit on every walkable cell of the pocket, so nothing can be set down. */
    private static void fillPocket(BattleSimulation sim, int pocketRadius) {
        int index = 0;
        for (int y = LZ_Y - pocketRadius; y <= LZ_Y + pocketRadius; y++) {
            for (int x = LZ_X - pocketRadius; x <= LZ_X + pocketRadius; x++) {
                sim.spawn(new EntitySpec("blocker" + index++, Faction.DEFENDER,
                        UnitType.MILITIA, x, y));
            }
        }
    }

    private static ShuttleMission landedSortie(BattleSimulation sim) {
        long id = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                LZ_X + 0.5f, LZ_Y + 0.5f, LZ_X + 0.5f, LZ_Y + 0.5f,
                -8f, -8f, /*pendingDelay*/ 0f);
        return sim.world().mission(id);
    }

    /**
     * A sealed landing zone ends the sortie instead of freezing it.
     *
     * <p>Twenty seconds of trying is the bound. What the craft must not do is
     * still be sitting there minutes later with the same passengers aboard.
     */
    @Test
    void aCraftThatCannotUnloadLeavesRatherThanParking() {
        BattleSimulation sim = pocketSim(2);
        fillPocket(sim, 2);
        // A marine somewhere unreachable, so the battle has two sides and keeps
        // ticking; the pocket is sealed, so nobody can walk to anybody.
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, LZ_X, LZ_Y));

        ShuttleMission mission = landedSortie(sim);
        for (int i = 0; i < 60 * 30; i++) sim.advance(1f / 30f);

        assertEquals(0, mission.deboardedThisSortie,
                "there was genuinely nowhere to put anybody");
        assertTrue(mission.state == ShuttleState.DEPARTING
                        || mission.state == ShuttleState.GONE,
                "the craft gave up and left rather than holding the LZ: "
                        + mission.state);
    }

    /**
     * A crowded landing zone still delivers, by reaching past the crowd.
     *
     * <p>This is the case that actually happens — a squad standing on its own
     * drop point rather than a sealed room — and the fix for it is reach, not
     * patience. Nobody should be given up on while there is somewhere to stand
     * a few cells further out.
     */
    @Test
    void aCrowdedLandingZoneIsReachedPast() {
        BattleSimulation sim = pocketSim(9);
        // Packed out to six cells, which puts every cell the old five-cell
        // reach could see under somebody's feet, with open ground past it.
        fillPocket(sim, 6);
        sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, LZ_X + 9, LZ_Y + 9));

        ShuttleMission mission = landedSortie(sim);
        int seats = mission.marinesRemaining;
        assertTrue(seats > 0, "the sortie is carrying somebody");
        for (int i = 0; i < 60 * 30; i++) sim.advance(1f / 30f);

        assertEquals(seats, mission.deboardedThisSortie,
                "everybody got off, further out rather than not at all");
    }
}
