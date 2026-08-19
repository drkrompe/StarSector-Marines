package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadFallbackSystem;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Node authoring defaults and structural-fallback coverage for Story H. */
public class TacticalNodeMustHoldTest {

    private static TacticalNode node(int x, boolean mustHold) {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST, x, 3,
                x, 3, x, 3, Faction.DEFENDER, 95, 4, mustHold);
    }

    @Test
    public void legacyConstructorDefaultsToOrdinaryNode() {
        TacticalNode node = new TacticalNode(TacticalNode.Kind.COMMAND_POST, 3, 3,
                3, 3, 3, 3, Faction.DEFENDER, 95, 4);
        assertFalse(node.mustHold, "kind and priority must not imply the mission flag");
    }

    @Test
    public void mustHoldNodeSuppressesFallbackLink() {
        FallbackFixture fixture = new FallbackFixture(true);
        fixture.system.tick();

        assertEquals(fixture.source, fixture.squad.assignedNode);
        assertFalse(fixture.squad.fallbackTriggered);
        assertFalse(fixture.squad.fallbackInProgress);
    }

    @Test
    public void ordinaryNodeStillConsumesFallbackLink() {
        FallbackFixture fixture = new FallbackFixture(false);
        fixture.system.tick();

        assertEquals(fixture.target, fixture.squad.assignedNode);
        assertTrue(fixture.squad.fallbackTriggered);
        assertTrue(fixture.squad.fallbackInProgress);
    }

    private static final class FallbackFixture {
        final TacticalNode source;
        final TacticalNode target;
        final Squad squad;
        final SquadFallbackSystem system;

        FallbackFixture(boolean mustHold) {
            NavigationGrid grid = new NavigationGrid(12, 8);
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
            }
            CellTopology topology = new CellTopology(grid.getWidth(), grid.getHeight());
            BattleSimulation sim = new BattleSimulation(grid, topology);
            NavigationService navigation = new NavigationService(grid, topology);

            source = node(3, mustHold);
            target = node(8, false);
            source.addLink(TacticalNode.LinkKind.FALLBACK_TO, target);

            long member = sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MARINE, 3, 3)
                    .home(3, 3));
            int squadId = sim.mintSquad(Faction.DEFENDER, member);
            sim.squad().assignSquad(member, squadId);
            squad = sim.getSquad(squadId);
            squad.originalSize = 4;
            squad.aliveMembers = 2;
            squad.assignedNode = source;

            system = new SquadFallbackSystem(navigation, sim.getRoster(), sim::clearPath);
        }
    }
}
