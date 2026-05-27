package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link ChokePointHold} on synthetic grids: portal-id stamping,
 * the on-cell concentrated-fire trigger, and the no-fire-when-trigger-false
 * branch.
 */
public class ChokePointHoldTest {

    private static final int W = 14;
    private static final int H = 14;

    /**
     * 14x14 grid with a 7x7 room (perimeter at x∈{3,9}, y∈{3,9}) and a single
     * doorway in the top wall at (6, 3). The room is the defender zone; the
     * corridor outside is the attacker side.
     */
    private static BattleSimulation singlePortalRoom() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 3; y <= 9; y++) {
            grid.setWalkable(3, y, false);
            grid.setWalkable(9, y, false);
        }
        for (int x = 3; x <= 9; x++) {
            grid.setWalkable(x, 3, false);
            grid.setWalkable(x, 9, false);
        }
        grid.setWalkableFloor(6, 3);
        grid.setDoorway(6, 3, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad defenderSquad(int id, float cx, float cy, int alive) {
        Squad squad = new Squad(id, Faction.DEFENDER);
        squad.aliveMembers = alive;
        squad.centroidX = cx;
        squad.centroidY = cy;
        return squad;
    }

    /**
     * Build a ChokePointHold + matching plan with the given LOS cells bound
     * to {@code losCell:i} slots, each filled with the corresponding member.
     * Returns the constructed plan so callers can advance / inspect it.
     */
    private static SquadPlan attachPlanWithLosCells(Squad squad, int portalId,
                                                    int portalX, int portalY,
                                                    List<int[]> cells, List<Unit> members) {
        ChokePointHold hold = new ChokePointHold(portalId, portalX, portalY, cells);
        SquadPlan.Step step = new SquadPlan.Step(hold);
        for (int i = 0; i < members.size() && i < cells.size(); i++) {
            List<Unit> bucket = new ArrayList<>(1);
            bucket.add(members.get(i));
            step.assignments.put(ChokePointHold.slotName(i), bucket);
        }
        List<SquadPlan.Step> steps = new ArrayList<>(1);
        steps.add(step);
        SquadPlan plan = new SquadPlan(steps);
        squad.currentPlan = plan;
        return plan;
    }

    @Test
    public void firstExecuteStampsPortalId() {
        BattleSimulation sim = singlePortalRoom();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1, 6f, 6f, 1);
        // Pre-condition: no portal scoped yet.
        assertEquals(-1, squad.chokePointPortalId);

        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        sim.addUnit(d1);

        // LOS cell at the marine's current cell so execute won't try to path away.
        List<int[]> cells = List.of(new int[]{5, 5});
        attachPlanWithLosCells(squad, portalId, 6, 3, cells, List.of(d1));

        ChokePointHold hold = (ChokePointHold) squad.currentPlan.currentStep().action;
        hold.execute(d1, squad, sim);

        assertEquals(portalId, squad.chokePointPortalId,
                "execute must stamp Squad.chokePointPortalId so the evaluator can scope the predicate");
    }

    @Test
    public void noFireWhenPortalCellEmpty() {
        BattleSimulation sim = singlePortalRoom();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1, 6f, 6f, 1);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        sim.addUnit(d1);

        List<int[]> cells = List.of(new int[]{5, 5});
        attachPlanWithLosCells(squad, portalId, 6, 3, cells, List.of(d1));
        ChokePointHold hold = (ChokePointHold) squad.currentPlan.currentStep().action;
        hold.execute(d1, squad, sim);

        assertEquals(0f, d1.getCooldownTimer(), 1e-6f,
                "no enemy on the portal cell → no shot, cooldown stays at zero");
        assertTrue(sim.getShotsThisFrame().isEmpty(),
                "no enemy on the portal cell → no shots emitted");
    }

    @Test
    public void firesWhenEnemyEntersPortalCell() {
        BattleSimulation sim = singlePortalRoom();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1, 6f, 6f, 1);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        sim.addUnit(d1);

        // Attacker stepping onto the doorway cell.
        Unit attacker = new Unit("a1", Faction.MARINE, UnitType.MARINE, 6, 3);
        sim.addUnit(attacker);

        List<int[]> cells = List.of(new int[]{5, 5});
        attachPlanWithLosCells(squad, portalId, 6, 3, cells, List.of(d1));
        ChokePointHold hold = (ChokePointHold) squad.currentPlan.currentStep().action;

        // Pre-check: defender has LoS to the doorway (no wall between (5,5) and (6,3) given the room shape).
        assertTrue(sim.getGrid().hasLineOfSight(5, 5, 6, 3),
                "test prerequisite: defender at (5,5) must have LoS to the doorway at (6,3)");

        hold.execute(d1, squad, sim);

        assertTrue(d1.getCooldownTimer() > 0f,
                "enemy on portal cell + LoS + range → defender must fire (cooldown set)");
        assertTrue(sim.getShotsThisFrame().size() > 0,
                "enemy on portal cell + LoS + range → at least one ShotEvent emitted");
    }

    @Test
    public void concentratedBurstFiresFromAllOnPostMembers() {
        BattleSimulation sim = singlePortalRoom();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1, 6f, 6f, 2);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        Unit d2 = new Unit("d2", Faction.DEFENDER, UnitType.MARINE, 7, 5);
        d2.squadId = squad.id;
        sim.addUnit(d1);
        sim.addUnit(d2);
        Unit attacker = new Unit("a1", Faction.MARINE, UnitType.MARINE, 6, 3);
        sim.addUnit(attacker);

        List<int[]> cells = List.of(new int[]{5, 5}, new int[]{7, 5});
        attachPlanWithLosCells(squad, portalId, 6, 3, cells, List.of(d1, d2));
        ChokePointHold hold = (ChokePointHold) squad.currentPlan.currentStep().action;

        assertTrue(sim.getGrid().hasLineOfSight(5, 5, 6, 3));
        assertTrue(sim.getGrid().hasLineOfSight(7, 5, 6, 3));

        // Drive each member's execute on the same tick. With the predicate
        // true and both holders on-post with LoS, both must fire — that's the
        // deterministic concentrated-burst property the action exists to
        // express.
        hold.execute(d1, squad, sim);
        hold.execute(d2, squad, sim);

        assertTrue(d1.getCooldownTimer() > 0f, "d1 must have fired this tick");
        assertTrue(d2.getCooldownTimer() > 0f, "d2 must have fired this tick");
        assertTrue(sim.getShotsThisFrame().size() >= 2,
                "two holders both fire → at least two ShotEvents");
    }

    @Test
    public void atPostMemberClearsPath() {
        BattleSimulation sim = singlePortalRoom();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1, 6f, 6f, 1);
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 5, 5);
        d1.squadId = squad.id;
        // Pretend the member had a stale path queued before getting to post.
        sim.addUnit(d1);

        List<int[]> cells = List.of(new int[]{5, 5});
        attachPlanWithLosCells(squad, portalId, 6, 3, cells, List.of(d1));
        ChokePointHold hold = (ChokePointHold) squad.currentPlan.currentStep().action;
        hold.execute(d1, squad, sim);

        assertTrue(d1.pathEmpty(), "on-post member should have its path cleared");
        assertEquals(0f, d1.getMoveProgress(), 1e-6f);
    }

    @Test
    public void transitMemberMovesTowardLosCell() {
        BattleSimulation sim = singlePortalRoom();
        int portalId = sim.getZoneGraph().getPortals().get(0).getPortalId();

        Squad squad = defenderSquad(1, 6f, 6f, 1);
        // Start the marine away from the LOS cell so they have to move.
        Unit d1 = new Unit("d1", Faction.DEFENDER, UnitType.MARINE, 7, 7);
        d1.squadId = squad.id;
        sim.addUnit(d1);

        List<int[]> cells = List.of(new int[]{5, 5});
        attachPlanWithLosCells(squad, portalId, 6, 3, cells, List.of(d1));
        ChokePointHold hold = (ChokePointHold) squad.currentPlan.currentStep().action;
        hold.execute(d1, squad, sim);

        assertEquals(portalId, squad.chokePointPortalId,
                "portal id is stamped even on transit ticks (idempotent)");
        // A path must be queued (member isn't at the LOS cell yet).
        assertNotEquals(0, d1.pathCellCount(),
                "transit branch must queue a path toward the bound LOS cell");
    }
}
