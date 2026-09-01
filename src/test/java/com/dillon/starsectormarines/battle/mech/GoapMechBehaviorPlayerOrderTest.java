package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.OrderCatalog;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendArea;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lance under a player's order plans the goal {@link OrderCatalog} names for
 * that order, looked up rather than competed for.
 *
 * <p>The distinction is invisible in the outcome today and is the point of the
 * test: area defence happens to score high enough to win the mech ladder's
 * MISSION bucket on its own, so a dispatcher that merely ran the ladder would
 * agree with this assertion by coincidence. The lance is put in front of a
 * contact so the role goals it would otherwise serve are genuinely in
 * contention, and the expectation is read out of the row so the next player
 * order — which will have no such luck — is covered by the same sentence.
 */
class GoapMechBehaviorPlayerOrderTest {

    @Test
    void aLancePlansTheGoalItsPlayerOrderNames() {
        BattleSimulation sim = openSimulation(80, 48);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long lead = spawn(sim, squadId, MechVariant.BULWARK,
                MechRole.BALANCED, 17, 20);
        spawn(sim, squadId, MechVariant.HOUND, MechRole.ASSAULT, 18, 20);
        Squad lance = sim.getSquad(squadId);
        lance.leaderId = lead;
        lance.aliveMembers = 2;
        lance.originalSize = 2;
        lance.centroidX = 17.5f;
        lance.centroidY = 20.5f;
        long contact = sim.spawn(new EntitySpec("contact", Faction.DEFENDER,
                UnitType.MARINE, 14, 20));
        SquadBeliefTestAccess.observeDirect(lance, contact, 14, 20,
                sim.getSimTickIndex());

        sim.getSquadMoveOrderService().requestDefendArea(squadId, 36, 20);
        sim.getSquadMoveOrderSystem().tick(sim);
        assertTrue(lance.hasPlayerTacticalOrder(AssignmentKind.DEFEND_AREA),
                "the order has to land on the lance or the test proves nothing");

        GoapMechBehavior.replanIfNeeded(lance, sim);

        assertSame(OrderCatalog.playerOrder(AssignmentKind.DEFEND_AREA).goal(),
                lance.currentGoal,
                "the lance serves the goal its order names, not whichever role "
                        + "goal the ladder would have preferred");
        assertInstanceOf(DefendArea.class,
                lance.currentPlan.currentStep().action);
    }

    private static long spawn(BattleSimulation sim, int squadId,
                              MechVariant variant, MechRole role,
                              int x, int y) {
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                variant.id + "-" + role.name(), Faction.MARINE,
                UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(role));
        return mech;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
