package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two moments at which a marine's situation changes faster than an
 * ordinary engagement: somebody opens at close quarters, and somebody takes
 * this marine as their target.
 *
 * <p>Both are asked of {@link TacticalScoring} directly. They are two bounded
 * queries over the spatial index, the sight test and the attacker index, and
 * standing up a battle to watch a screen go up would prove the case rather
 * than the mechanism.
 */
class ContactOnsetTest {

    private static final int W = 48;
    private static final int H = 48;
    private static final int SELF_X = 20;
    private static final int SELF_Y = 20;

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** Inert by role, so what is measured is the query and not the unit's own AI. */
    private static long unit(BattleSimulation sim, Faction faction, int x, int y) {
        return sim.spawn(new EntitySpec(faction + "-" + x + "-" + y + "-"
                + sim.liveUnitCount(), faction, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE));
    }

    private static long opening(BattleSimulation sim, long self) {
        return sim.getTacticalScoring().closeContactOpening(
                self, TacticalScoring.CLOSE_QUARTERS_CELLS);
    }

    @Test
    void nothingIsOpeningOnAnEmptyField() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        assertEquals(0L, opening(sim, self), "nobody is there");
    }

    @Test
    void aHostileInsideRoomRangeIsTheMomentAndOneBeyondItIsNot() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        long far = unit(sim, Faction.DEFENDER, SELF_X + 20, SELF_Y);
        assertEquals(0L, opening(sim, self),
                "twenty cells off is a field, and a screen is not the answer to it");

        long near = unit(sim, Faction.DEFENDER, SELF_X + 4, SELF_Y);
        assertEquals(near, opening(sim, self),
                "four cells off is a room, whatever else is on the map");
        assertEquals(0L, sim.getTacticalScoring().closeContactOpening(far, 1f),
                "and the query is about the asker, not about the map");
    }

    @Test
    void theNearestOfSeveralIsTheOneThatMatters() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        unit(sim, Faction.DEFENDER, SELF_X + 8, SELF_Y);
        long nearest = unit(sim, Faction.DEFENDER, SELF_X + 2, SELF_Y);
        unit(sim, Faction.DEFENDER, SELF_X, SELF_Y + 6);
        assertEquals(nearest, opening(sim, self),
                "the bearing worth putting something on is the closest one");
    }

    @Test
    void aFriendAtArmsLengthIsNotAContact() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        unit(sim, Faction.MARINE, SELF_X + 1, SELF_Y);
        assertEquals(0L, opening(sim, self), "that is a squadmate");
    }

    @Test
    void aHostileBehindAWallIsNotSomethingTheMarineHasSeen() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        for (int y = SELF_Y - 4; y <= SELF_Y + 4; y++) {
            grid.setWalkable(SELF_X + 2, y, false);
        }
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        unit(sim, Faction.DEFENDER, SELF_X + 4, SELF_Y);
        assertEquals(0L, opening(sim, self),
                "the spatial index knows where everybody is; the marine does not");
    }

    @Test
    void nobodyIsAimingAtAMarineNobodyIsAimingAt() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        unit(sim, Faction.DEFENDER, SELF_X + 30, SELF_Y);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(0L, sim.getTacticalScoring().takenAsATarget(self));
    }

    @Test
    void aShooterDownALaneIsKnownBeforeItsFirstRoundArrives() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        // Far enough that this is a lane rather than a room: the close-quarters
        // moment cannot see it, which is the whole reason this one exists.
        long sniper = unit(sim, Faction.DEFENDER, SELF_X + 30, SELF_Y);
        sim.world().setTargetId(sniper, self);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(sniper, sim.getTacticalScoring().takenAsATarget(self),
                "somebody has taken this marine, and no round has had to land");
        assertEquals(0L, opening(sim, self),
                "and the close-quarters moment is silent, as it should be");
    }

    @Test
    void theNearestOfSeveralShootersSetsTheBearing() {
        BattleSimulation sim = openArena();
        long self = unit(sim, Faction.MARINE, SELF_X, SELF_Y);
        long far = unit(sim, Faction.DEFENDER, SELF_X + 30, SELF_Y);
        long near = unit(sim, Faction.DEFENDER, SELF_X, SELF_Y + 14);
        sim.world().setTargetId(far, self);
        sim.world().setTargetId(near, self);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(near, sim.getTacticalScoring().takenAsATarget(self),
                "a marine with one screen puts it on the most pressing bearing");
    }
}
