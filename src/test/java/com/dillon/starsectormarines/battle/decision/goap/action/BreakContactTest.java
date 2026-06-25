package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link BreakContact}'s execute semantics: destination-cell
 * caching, transit vs. hold branches, and the re-pick rule (hold when the
 * cached cell is hidden, re-roll when it's exposed — including after
 * arrival, since the original "stick once arrived" rule glued morale-broken
 * units to bad picks in tight indoor corridors).
 */
public class BreakContactTest {

    private static final int W = 14;
    private static final int H = 14;

    /**
     * 14x14 open floor with a partial wall at column 7 spanning rows 3..11 so
     * there's a clearly-hidden side. Marines on the left (cols 0..6); enemies
     * on the right (cols 8..13). The wall gives findFallbackPosition something
     * to put behind.
     */
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

    private static Entity marineAt(int x, int y, int squadId) {
        Entity u = new Entity("m", Faction.MARINE, UnitType.MARINE, x, y);
        u.squadId = squadId;
        return u;
    }

    private static Entity defenderAt(int x, int y) {
        Entity u = new Entity("d", Faction.DEFENDER, UnitType.MARINE, x, y);
        return u;
    }

    @Test
    public void executePicksAndStashesDestinationOnFirstTick() {
        BattleSimulation sim = walledSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;

        Entity marine = marineAt(6, 7, 1);
        sim.addUnit(marine);
        sim.world().setFallbackCell(marine.entityId, -1, -1);
        // One defender on the other side of the wall — gives findFallbackPosition
        // a threat to hide from.
        sim.addUnit(defenderAt(10, 7));

        ActionStatus status = BreakContact.INSTANCE.execute(marine, squad, sim);
        assertEquals(ActionStatus.RUNNING, status, "BreakContact runs perpetually");
        assertTrue(sim.world().fallbackCellX(marine.entityId) >= 0 && sim.world().fallbackCellY(marine.entityId) >= 0,
                "the action must have stashed a destination cell on the unit");
    }

    @Test
    public void atDestinationClearsPathAndHolds() {
        BattleSimulation sim = walledSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;

        Entity marine = marineAt(2, 2, 1);
        sim.addUnit(marine);
        // Force destination to current cell — simulates "already arrived."
        sim.world().setFallbackCell(marine.entityId, 2, 2);
        sim.addUnit(defenderAt(11, 11));

        BreakContact.INSTANCE.execute(marine, squad, sim);
        assertTrue(Paths.isEmpty(sim.world().path(marine.entityId)), "arrived → no path should be queued");
        assertEquals(0f, sim.world().moveProgress(marine.entityId), 1e-6f,
                "arrived → moveProgress reset, render position pinned");
    }

    @Test
    public void destinationHoldsWhenArrivedAtHiddenCell() {
        BattleSimulation sim = walledSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;

        // Marine on the hidden side of the column-7 wall; defender on the
        // other side. Cell (10, 7) is in shadow of the wall from (3, 7), so
        // the cached destination is genuinely hidden and must stick.
        Entity marine = marineAt(10, 7, 1);
        sim.addUnit(marine);
        sim.world().setFallbackCell(marine.entityId, 10, 7);
        sim.addUnit(defenderAt(3, 7));

        BreakContact.INSTANCE.execute(marine, squad, sim);
        assertEquals(10, sim.world().fallbackCellX(marine.entityId),
                "hidden destination must hold — the picker's distFromSelf bias is what prevents churn between equally-good cells");
        assertEquals(7, sim.world().fallbackCellY(marine.entityId));
    }

    @Test
    public void recomputesWhenArrivedAtCellThatIsStillExposed() {
        BattleSimulation sim = walledSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;

        // Marine has "arrived" at (2, 2) but a defender at (5, 2) has clear
        // LoS along row 2 — the cell isn't a real hide. Used to be sticky;
        // now the action must re-evaluate so the morale-broken squad doesn't
        // glue itself into the kill zone.
        Entity marine = marineAt(2, 2, 1);
        sim.addUnit(marine);
        sim.world().setFallbackCell(marine.entityId, 2, 2);
        sim.addUnit(defenderAt(5, 2));

        BreakContact.INSTANCE.execute(marine, squad, sim);
        boolean cellChanged = sim.world().fallbackCellX(marine.entityId) != 2 || sim.world().fallbackCellY(marine.entityId) != 2;
        assertTrue(cellChanged,
                "arrived destination was exposed to (5,2); re-pick should have moved fallbackCellX/Y off (2,2)");
    }

    @Test
    public void recomputesWhenCachedCellIsCompromisedMidTransit() {
        BattleSimulation sim = walledSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;

        // Marine at (2, 2), heading to a cached cell at (3, 3) which is in
        // full LoS of the defender at (5, 2). Marine isn't AT the cell yet, so
        // the action should recompute. The new destination is whatever
        // findFallbackPosition picks — the only guarantee is "it's different
        // from (3, 3) OR (3, 3) became hidden between ticks." For this open
        // patch the defender has LoS to (3, 3), so the action must replace it.
        Entity marine = marineAt(2, 2, 1);
        sim.addUnit(marine);
        sim.world().setFallbackCell(marine.entityId, 3, 3);
        sim.addUnit(defenderAt(5, 2));

        BreakContact.INSTANCE.execute(marine, squad, sim);
        // Either the cell changed, or it's still visible — but in this layout
        // the defender at (5,2) has LoS to (3,3). The recompute branch should
        // have picked a different cell behind the column-7 wall.
        boolean changed = (sim.world().fallbackCellX(marine.entityId) != 3 || sim.world().fallbackCellY(marine.entityId) != 3);
        assertTrue(changed,
                "cached (3,3) was visible to (5,2); recompute should have moved the destination");
    }

    @Test
    public void preconditionsAndEffectsAreEmpty() {
        // BreakContact is a custom-plan action — the backward-chaining planner
        // never sees it, so empty pre/eff is intentional.
        assertEquals(WorldState.EMPTY, BreakContact.INSTANCE.preconditions(),
                "BreakContact preconditions are empty — only customPlan emits this action");
        assertEquals(WorldState.EMPTY, BreakContact.INSTANCE.effects(),
                "BreakContact effects are empty — customPlan owns the goal contract");
    }

    @Test
    public void requiredMembersIsOne() {
        assertEquals(1, BreakContact.INSTANCE.requiredMembers(),
                "BreakContact is per-member — RoleAssigner fills the default 'any' slot with the alive squad");
    }
}
