package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A player's order to a mech's chassis and one to its lance: the newer replaces the older. */
class MechOrderPrecedenceTest {

    @Test
    void aMoveToTheMechEndsItsLancesDefendArea() {
        BattleSimulation sim = openSimulation(40, 40);
        long mech = mechSquad(sim, 5, 5);
        int lance = sim.squad().squadId(mech);
        sim.getSquadMoveOrderService().requestDefendArea(lance, 20, 20);
        sim.getSquadMoveOrderSystem().tick(sim);
        assertInstanceOf(ActiveDefendAreaOrder.class, sim.getSquadMoveOrderService().activeOrder(lance));

        sim.getMechMoveOrderService().requestMove(mech, 30, 10);
        sim.getMechMoveOrderSystem().tick(sim);

        assertNotNull(sim.getMechMoveOrderService().activeOrder(mech));
        assertNull(sim.getSquadMoveOrderService().activeOrder(lance), "the move replaces the Defend Area");
    }

    @Test
    void aDefendAreaOnTheLanceEndsTheMechsMove() {
        BattleSimulation sim = openSimulation(40, 40);
        long mech = mechSquad(sim, 5, 5);
        int lance = sim.squad().squadId(mech);
        sim.getMechMoveOrderService().requestMove(mech, 30, 10);
        sim.getMechMoveOrderSystem().tick(sim);
        assertNotNull(sim.getMechMoveOrderService().activeOrder(mech));

        sim.getSquadMoveOrderService().requestDefendArea(lance, 20, 20);
        sim.getSquadMoveOrderSystem().tick(sim);

        assertInstanceOf(ActiveDefendAreaOrder.class, sim.getSquadMoveOrderService().activeOrder(lance));
        assertNull(sim.getMechMoveOrderService().activeOrder(mech), "the Defend Area replaces the move");
    }

    @Test
    void aMoveToOneMechOfALanceLeavesTheLancesDefendAreaToTheOthers() {
        BattleSimulation sim = openSimulation(40, 40);
        long moved = mechSquad(sim, 5, 5);
        int lance = sim.squad().squadId(moved);
        long other = sim.spawn(MechVariant.BULWARK.applyTo(new EntitySpec(
                "mech-" + sim.liveUnitCount(), Faction.MARINE, UnitType.HEAVY_MECH, 8, 5).squad(lance)));
        sim.world().attachMechLoadout(other, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
        sim.getSquad(lance).aliveMembers = 2;
        sim.getSquadMoveOrderService().requestDefendArea(lance, 20, 20);
        sim.getSquadMoveOrderSystem().tick(sim);

        sim.getMechMoveOrderService().requestMove(moved, 30, 10);
        sim.getMechMoveOrderSystem().tick(sim);

        assertInstanceOf(ActiveDefendAreaOrder.class, sim.getSquadMoveOrderService().activeOrder(lance),
                "the lancemate still holds the area");
    }

    private static long mechSquad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        MechVariant variant = MechVariant.BULWARK;
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                "mech-" + sim.liveUnitCount(), Faction.MARINE, UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(MechRole.BALANCED));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = sim.world().x(mech);
        squad.centroidY = sim.world().y(mech);
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
