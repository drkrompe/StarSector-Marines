package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story A: {@link BreakLOS} — corner-duck action picked when a squadmate is
 * taking fire from a visible shooter. Verifies destination caching, transit
 * vs. arrival branches, and the UNDER_FIRE → no-LoS effect contract.
 */
public class BreakLOSTest {

    private static final int W = 14;
    private static final int H = 14;

    /** Open floor with a partial wall at column 7 (rows 3..11) so there's a hidden side. */
    private static BattleSimulation walledSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 3; y <= 11; y++) {
            grid.setWalkable(7, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Unit marineAt(int x, int y, int squadId) {
        Unit u = new Unit("m", Faction.MARINE, UnitType.MARINE, x, y);
        u.squadId = squadId;
        return u;
    }

    private static Unit defenderAt(int x, int y) {
        return new Unit("d", Faction.DEFENDER, UnitType.MARINE, x, y);
    }

    @Test
    public void preconditionsRequireUnderFire() {
        WorldState pre = BreakLOS.INSTANCE.preconditions();
        assertTrue(pre.isSpecified(Predicate.UNDER_FIRE_AT_LOS));
        assertTrue(pre.get(Predicate.UNDER_FIRE_AT_LOS));
    }

    @Test
    public void effectsClearUnderFire() {
        WorldState eff = BreakLOS.INSTANCE.effects();
        assertTrue(eff.isSpecified(Predicate.UNDER_FIRE_AT_LOS));
        assertFalse(eff.get(Predicate.UNDER_FIRE_AT_LOS));
    }

    @Test
    public void executePicksAndStashesDestinationOnFirstTick() {
        BattleSimulation sim = walledSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        // Marine on the exposed side (row 2 — wall column 7 is only walls for
        // rows 3..11, so row 2 is open across the entire column). Defender at
        // (10, 2) has LOS to the marine; findFallbackPosition must pick a
        // different (hidden) cell.
        Unit marine = marineAt(2, 2, squadId);
        sim.addUnit(marine);
        sim.world().setFallbackCell(marine.entityId, -1, -1);
        sim.addUnit(defenderAt(11, 7));

        ActionStatus status = BreakLOS.INSTANCE.execute(marine, squad, sim);
        assertTrue(sim.world().fallbackCellX(marine.entityId) >= 0 && sim.world().fallbackCellY(marine.entityId) >= 0,
                "BreakLOS must stash a destination cell on the unit");
        // Status is RUNNING if the picked cell differs from the start cell,
        // SUCCESS if findFallbackPosition decided the start cell was already
        // hidden (in which case the unit was effectively already safe).
        boolean different = (sim.world().fallbackCellX(marine.entityId) != 2 || sim.world().fallbackCellY(marine.entityId) != 2);
        if (different) {
            assertEquals(ActionStatus.RUNNING, status,
                    "in-transit BreakLOS reports RUNNING while the member walks to its hidden cell");
        } else {
            assertEquals(ActionStatus.SUCCESS, status,
                    "if the start cell was already hidden, arrival logic flips to SUCCESS immediately");
        }
    }

    @Test
    public void arrivalReturnsSuccessAndPinsPosition() {
        BattleSimulation sim = walledSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(2, 2, squadId);
        sim.addUnit(marine);
        // Force "arrived" — fallback cell == current cell.
        sim.world().setFallbackCell(marine.entityId, 2, 2);
        sim.addUnit(defenderAt(11, 11));

        ActionStatus status = BreakLOS.INSTANCE.execute(marine, squad, sim);
        assertEquals(ActionStatus.SUCCESS, status,
                "arrived → BreakLOS returns SUCCESS so the plan advances and the next replan can pick Overwatch/Engage");
        assertTrue(marine.pathEmpty(), "arrived → no path");
        assertEquals(0f, sim.world().moveProgress(marine.entityId), 1e-6f);
        assertEquals(sim.world().cellX(marine.entityId), marine.getRenderX(), 1e-6f);
        assertEquals(sim.world().cellY(marine.entityId), marine.getRenderY(), 1e-6f);
    }

    @Test
    public void cachedDestinationIsHonoredOnSubsequentTicks() {
        // Once a destination is cached, BreakLOS doesn't re-pick on subsequent
        // ticks — it keeps walking. We force a specific cached destination and
        // verify the action reports RUNNING (not arrived) while pre-cached.
        BattleSimulation sim = walledSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(5, 7, squadId);
        sim.addUnit(marine);
        // Pre-cache a destination that's not the current cell.
        sim.world().setFallbackCell(marine.entityId, 4, 7);
        sim.addUnit(defenderAt(11, 7));

        ActionStatus status = BreakLOS.INSTANCE.execute(marine, squad, sim);
        assertEquals(ActionStatus.RUNNING, status,
                "pre-cached destination, not arrived → RUNNING");
        assertEquals(4, sim.world().fallbackCellX(marine.entityId), "cached destination must not be recomputed mid-transit");
        assertEquals(7, sim.world().fallbackCellY(marine.entityId));
    }

    @Test
    public void recomputesWhenCachedCellIsExposedToAnEnemy() {
        // SQ-17 regression: pre-fix, BreakLOS only re-picked when the cached
        // cell was unset (< 0). If the picker landed on a borderline cell
        // that an enemy could still see (or the threat repositioned mid-
        // transit), the unit stayed glued to it. Now we share BreakContact's
        // refresh rule via TacticalScoring.fallbackDestinationNeedsRefresh.
        BattleSimulation sim = walledSim();
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;

        Unit marine = marineAt(2, 2, squadId);
        sim.addUnit(marine);
        // Cached destination on the open side of the map — fully visible to
        // the defender at (5, 2). Pre-fix: held this cell forever.
        sim.world().setFallbackCell(marine.entityId, 3, 2);
        sim.addUnit(defenderAt(5, 2));

        BreakLOS.INSTANCE.execute(marine, squad, sim);
        boolean changed = (sim.world().fallbackCellX(marine.entityId) != 3 || sim.world().fallbackCellY(marine.entityId) != 2);
        assertTrue(changed,
                "cached (3,2) was visible to defender at (5,2); refresh should have picked a new cell");
    }

    @Test
    public void requiredMembersIsOne() {
        assertEquals(1, BreakLOS.INSTANCE.requiredMembers());
    }
}
