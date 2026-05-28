package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link BreachToEngage}. Verifies the relevance gate
 * (across-portal target + no in-zone enemies + not garrison + not broken)
 * and that customPlan emits a single {@link BreachAndAdvance} step with the
 * right portal id.
 */
public class BreachToEngageTest {

    private static final int W = 14;
    private static final int H = 14;

    /**
     * Two zones split by a wall at column 7 with a single doorway at (7, 6).
     * Left half is the "marine spawn" zone, right half is the "target" zone.
     */
    private static BattleSimulation twoZoneSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 7) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(7, 6);
        grid.setDoorway(7, 6, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marineSquadAt(BattleSimulation sim, int memberCount, float cx, float cy) {
        // Leader (first member) is placed at the centroid so the post-leader-
        // anchor squadCurrentZone resolves to the same zone the centroid would
        // have. Other members nearby in row-major.
        int lx = Math.round(cx);
        int ly = Math.round(cy);
        Unit first = new Unit("m0", Faction.MARINE, UnitType.MARINE, lx, ly);
        int sid = sim.mintSquad(Faction.MARINE, first);
        first.squadId = sid;
        sim.addUnit(first);
        for (int i = 1; i < memberCount; i++) {
            Unit u = new Unit("m" + i, Faction.MARINE, UnitType.MARINE, lx + i, ly);
            u.squadId = sid;
            sim.addUnit(u);
        }
        Squad sq = sim.getSquad(sid);
        sq.aliveMembers = memberCount;
        sq.centroidX = cx;
        sq.centroidY = cy;
        sq.originalSize = memberCount;
        return sq;
    }

    @Test
    public void priorityIsEngagement() {
        assertEquals(Goal.Priority.ENGAGEMENT, BreachToEngage.INSTANCE.priority());
    }

    @Test
    public void relevanceZeroWhenGarrisonRouted() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        squad.holdsFireUntilKillZone = true;
        // Across-portal target.
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 11, 6));

        assertEquals(0f, BreachToEngage.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "garrison squads hold position — never breach");
    }

    @Test
    public void relevanceZeroWhenMoraleBroken() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        squad.moraleBroken = true;
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 11, 6));

        assertEquals(0f, BreachToEngage.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "broken squads pull back via SurviveContact — they don't breach");
    }

    @Test
    public void relevanceZeroWhenNoTarget() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        // No enemies on the map.
        assertEquals(0f, BreachToEngage.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "no target → no breach");
    }

    @Test
    public void relevanceZeroWhenTargetInSameZone() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        // Enemy in the squad's zone — should engage normally, not breach.
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 5, 6));

        assertEquals(0f, BreachToEngage.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "in-zone target → EliminateEnemies handles it");
    }

    @Test
    public void relevancePositiveAcrossPortalNoInZone() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        // Defender on the far side of the doorway, marines see them.
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 11, 6));

        assertTrue(BreachToEngage.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "across-portal target with no in-zone alternative → breach fires");
    }

    @Test
    public void relevanceZeroWhenAcrossPortalButInZoneEnemyVisible() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        // Two enemies: one in-zone (visible), one across portal.
        sim.addUnit(new Unit("close", Faction.DEFENDER, UnitType.MARINE, 5, 6));
        sim.addUnit(new Unit("far",   Faction.DEFENDER, UnitType.MARINE, 11, 6));

        assertEquals(0f, BreachToEngage.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "in-zone enemy takes precedence — engage them first before breaching");
    }

    @Test
    public void customPlanEmitsSingleBreachStep() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 2, 3f, 6f);
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 11, 6));

        SquadPlan plan = BreachToEngage.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "breach scenario should yield a plan");
        assertEquals(1, plan.stepCount());
        assertTrue(plan.steps().get(0).action instanceof BreachAndAdvance,
                "the step must be a BreachAndAdvance instance");
    }

    @Test
    public void customPlanCarriesPortalAndCellArrays() {
        BattleSimulation sim = twoZoneSim();
        Squad squad = marineSquadAt(sim, 3, 3f, 6f);
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 11, 6));

        SquadPlan plan = BreachToEngage.INSTANCE.customPlan(squad, sim);
        BreachAndAdvance action = (BreachAndAdvance) plan.steps().get(0).action;
        assertEquals(3, action.slotCount(),
                "slot count = alive squad members so every marine gets a stack-up + forward cell pair");
        // Doorway is at (7, 6). Forward cells should be x > 7 (in the target zone).
        for (int i = 0; i < action.slotCount(); i++) {
            assertTrue(action.forwardCellX(i) > 7,
                    "forward cell " + i + " should be on the target side of the doorway, got x="
                            + action.forwardCellX(i));
        }
    }

    @Test
    public void customPlanReturnsNullWhenAlreadyInTargetZone() {
        BattleSimulation sim = twoZoneSim();
        // Squad centroid on the right side of the doorway already.
        Squad squad = marineSquadAt(sim, 2, 11f, 6f);
        sim.addUnit(new Unit("e", Faction.DEFENDER, UnitType.MARINE, 12, 6));

        assertNull(BreachToEngage.INSTANCE.customPlan(squad, sim),
                "squad already in target zone → no portal hop needed");
    }
}
