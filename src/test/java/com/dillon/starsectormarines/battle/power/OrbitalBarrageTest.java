package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrbitalBarrageTest {

    private static final int W = 24;
    private static final int H = 24;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void insufficientCampaignSuppliesPreventCommit() {
        BattleSimulation sim = openSim();
        OrbitalBarrage power = new OrbitalBarrage();
        Stockpile stockpile = new Stockpile(OrbitalBarrage.SUPPLY_COST - 1);
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(stockpile);

        assertFalse(sim.getCommandPowerService().canActivate(power));
        sim.getCommandPowerService().requestActivation(power.id, 10, 10);
        new CommandPowerSystem(sim.getCommandPowerService(), sim).tick(BattleSimulation.TICK_DT);

        assertEquals(4f + 0.5f * BattleSimulation.TICK_DT,
                sim.getCommandPowerService().getCommandPoints(), 0.001f);
        assertEquals(1, sim.getCommandPowerService().getChargesRemaining(power.id));
        assertTrue(sim.getCommandPowerService().getActiveFireMissions().isEmpty());
        assertTrue(sim.getHeavyImpactsThisFrame().isEmpty());
        assertEquals(OrbitalBarrage.SUPPLY_COST - 1, stockpile.supplies);
    }

    @Test
    public void telegraphThenHeavyBlastHitsBothSidesBreachesWallsAndRespectsRoofs() {
        BattleSimulation sim = openSim();
        long friendly = sim.spawn(new EntitySpec("friendly", Faction.MARINE,
                UnitType.MARINE, 10, 10));
        long enemy = sim.spawn(new EntitySpec("enemy", Faction.DEFENDER,
                UnitType.MILITIA, 12, 10));
        long roofed = sim.spawn(new EntitySpec("roofed", Faction.DEFENDER,
                UnitType.MILITIA, 10, 12));
        sim.getTopology().setBuildingId(10, 12, 7);

        sim.getGrid().setWalkable(9, 10, false);
        sim.getGrid().setWallHp(9, 10, 100);
        sim.getTopology().setWall(9, 10, true);

        OrbitalBarrage power = new OrbitalBarrage();
        Stockpile stockpile = new Stockpile(20);
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(stockpile);
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);
        sim.getCommandPowerService().requestActivation(power.id, 10, 10);

        system.tick(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getCommandPowerService().getActiveFireMissions().size());
        assertEquals(15, stockpile.supplies);
        assertEquals(0, sim.getCommandPowerService().getChargesRemaining(power.id));
        assertEquals(UnitType.MARINE.maxHp, sim.world().hp(friendly), 0.001f,
                "the warning period applies no early damage");

        int ticks = (int) Math.ceil(OrbitalBarrage.WARNING_SECONDS / BattleSimulation.TICK_DT);
        for (int i = 0; i < ticks; i++) system.tick(BattleSimulation.TICK_DT);

        assertTrue(sim.getCommandPowerService().getActiveFireMissions().isEmpty());
        assertEquals(1, sim.getHeavyImpactsThisFrame().size(),
                "impact creates synchronized presentation feedback");
        assertEquals(0L, sim.resolveUnit(friendly), "orbital fire has real friendly fire");
        assertEquals(0L, sim.resolveUnit(enemy), "enemy in the marked zone takes the blast");
        assertEquals(roofed, sim.resolveUnit(roofed), "an intact roof shields aerial ordnance");
        assertTrue(sim.getGrid().isWalkable(9, 10), "the heavy shell breaches ordinary walls");
    }

    private static final class Stockpile implements CommandPowerResources {
        int supplies;

        Stockpile(int supplies) { this.supplies = supplies; }

        @Override public int availableSupplies() { return supplies; }

        @Override
        public boolean spendSupplies(int amount) {
            if (amount < 0 || supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }
}
