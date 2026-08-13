package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.InfantryPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarineInsertionTest {

    @Test
    public void valkyrieDeliversOneCommanderVisibleEightMarineSquad() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(30, 30));
        sim.spawn(new EntitySpec("battle-keeper", Faction.DEFENDER, UnitType.MILITIA, 26, 26)
                .health(10_000f).attackDamage(0f));
        MarineInsertion power = new MarineInsertion();
        Stockpile stockpile = new Stockpile(10);
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(stockpile);
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertSame(InfantryPayload.INSTANCE, mission.payload);
        assertEquals(8, mission.marinesRemaining);
        assertEquals(1f + 0.5f * BattleSimulation.TICK_DT,
                sim.getCommandPowerService().getCommandPoints(), 0.001f);
        assertEquals(0, sim.getCommandPowerService().getChargesRemaining(power.id));
        assertEquals(8, stockpile.supplies);

        for (int i = 0; i < 1200 && mission.deboardedThisSortie < 8; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(8, mission.deboardedThisSortie);
        Squad squad = sim.getSquad(mission.squadId);
        assertNotNull(squad);
        assertEquals(8, squad.originalSize);
        assertTrue(squad.leaderId != 0L);
        int members = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE
                    && sim.squad().hasSquad(unit)
                    && sim.squad().squadId(unit) == squad.id) members++;
        }
        assertEquals(8, members);
    }

    private static final class Stockpile implements CommandPowerResources {
        int supplies;

        Stockpile(int supplies) { this.supplies = supplies; }

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }
}
