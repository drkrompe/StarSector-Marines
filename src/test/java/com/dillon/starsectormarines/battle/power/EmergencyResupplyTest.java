package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ResupplyPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmergencyResupplyTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(30, 30));
    }

    @Test
    public void physicalCarrierDeliversPersistentCache() {
        BattleSimulation sim = openSim();
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MILITIA, 28, 28));
        EmergencyResupply power = new EmergencyResupply();
        sim.setCommandPowers(List.of(power));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);

        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(1, sim.getAirEntityIds().length, "a real utility shuttle enters first");
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertSame(ResupplyPayload.INSTANCE, mission.payload);
        assertEquals(1, mission.marinesRemaining);
        assertEquals(1, sim.getCommandPowerService().getChargesRemaining(power.id));

        for (int i = 0; i < 700 && sim.getResupplyCaches().isEmpty(); i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(1, sim.getResupplyCaches().size(), "touchdown leaves one persistent cache");
        assertTrue(sim.getResupplyCaches().get(0).stock > 0);
    }
}
