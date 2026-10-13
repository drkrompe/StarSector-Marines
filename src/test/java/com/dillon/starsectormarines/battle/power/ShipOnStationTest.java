package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.MissionStance;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipOnStationTest {

    private static final int W = 160;
    private static final int H = 90;
    private static final ShipSupportSource FIRST = ShipSupportSource.stock("onslaught", "onslaught_Standard", "Anvil", false);
    private static final ShipSupportSource SECOND = ShipSupportSource.stock("paragon", "paragon_Elite", "Bulwark", false);
    private static final ShipSupportSource FOLD = ShipSupportSource.stock("shepherd", null, "Fold", true);

    @BeforeEach
    void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    /** Open ground with one marine at (30, 45) who sees the cell (34, 45) and a defender far off. */
    private static BattleSimulation sim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 30, 45));
        sim.spawn(new EntitySpec("far", Faction.DEFENDER, UnitType.MILITIA, 150, 80));
        for (int i = 0; i < 10; i++) sim.advance(BattleSimulation.TICK_DT);
        return sim;
    }

    private static final class Stockpile implements CommandPowerResources {
        int supplies;

        Stockpile(int supplies) { this.supplies = supplies; }

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (amount < 0 || supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }

    private static void tick(BattleSimulation sim, CommandPowerSystem system, int ticks) {
        for (int i = 0; i < ticks; i++) system.tick(BattleSimulation.TICK_DT);
    }

    @Test
    void anUnseenCellIsRefusedAndNothingIsSpent() {
        BattleSimulation sim = sim();
        ShipOnStation power = new ShipOnStation(List.of(FIRST));
        Stockpile stockpile = new Stockpile(20);
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(stockpile);
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);

        assertTrue(sim.playerSeesCell(34, 45), "the marine sees the near ground");
        assertFalse(sim.playerSeesCell(120, 10), "and not the far corner");
        assertFalse(power.canTarget(120, 10, sim));

        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 120, 10);
        system.tick(BattleSimulation.TICK_DT);

        assertEquals(20, stockpile.supplies);
        assertEquals(1, sim.getCommandPowerService().getChargesRemaining(ShipOnStation.ID));
        assertTrue(sim.warships().ships().isEmpty());
    }

    @Test
    void anActivationSpendsOneChargeAndFitsTheNextSourceInOrder() {
        BattleSimulation sim = sim();
        ShipOnStation power = new ShipOnStation(List.of(FIRST, SECOND));
        Stockpile stockpile = new Stockpile(40);
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(stockpile);
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);
        assertEquals(2, sim.getCommandPowerService().getChargesRemaining(ShipOnStation.ID));

        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        system.tick(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getCommandPowerService().getChargesRemaining(ShipOnStation.ID));
        assertEquals(40 - ShipOnStation.SUPPLY_COST, stockpile.supplies);
        assertEquals(1, sim.warships().ships().size());
        assertEquals("onslaught", sim.warships().ships().get(0).hullId);

        tick(sim, system, (int) (ShipOnStation.COOLDOWN_SECONDS / BattleSimulation.TICK_DT) + 2);
        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        system.tick(BattleSimulation.TICK_DT);

        assertEquals(0, sim.getCommandPowerService().getChargesRemaining(ShipOnStation.ID));
        assertEquals("paragon", sim.warships().ships().get(1).hullId);

        tick(sim, system, (int) (ShipOnStation.COOLDOWN_SECONDS / BattleSimulation.TICK_DT) + 2);
        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        system.tick(BattleSimulation.TICK_DT);
        assertEquals(2, sim.warships().ships().size(), "a spent power calls nothing more");
        assertEquals(40 - 2 * ShipOnStation.SUPPLY_COST, stockpile.supplies);
    }

    @Test
    void theShipComesInOnTheCompanysSideFacingItsStation() {
        BattleSimulation sim = sim();
        sim.setCommandPowers(List.of(new ShipOnStation(List.of(FIRST))));
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);

        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        system.tick(BattleSimulation.TICK_DT);

        Warship ship = sim.warships().ships().get(0);
        assertTrue(ship.x < 34.5f, "the marines come from the west, was " + ship.x);
        assertEquals(AirBody.facingToward(34.5f - ship.x, 45.5f - ship.y), ship.facingDegrees, 0.001f,
                "nose on its station");
        assertEquals(34.5f, ship.stationX(), 0.001f);
        assertEquals(45.5f, ship.stationY(), 0.001f);
        assertEquals(ShipOnStation.STATION_SECONDS, ship.stationSecondsRemaining(), 0.001f);
        assertTrue(ship.isDirected(), "the director picks its own ground");
        assertTrue(ship.hasDoctrine());
        assertEquals(Faction.MARINE, ship.faction);
    }

    @Test
    void theCompanysMissionSideSetsTheStance() {
        BattleSimulation attack = sim();
        attack.setCommandPowers(List.of(new ShipOnStation(List.of(FIRST))));
        attack.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        new CommandPowerSystem(attack.getCommandPowerService(), attack).tick(BattleSimulation.TICK_DT);
        assertEquals(MissionStance.ATTACK, attack.warships().ships().get(0).stance());

        BattleSimulation defend = sim();
        defend.setCompanyStance(MissionStance.DEFEND);
        defend.setCommandPowers(List.of(new ShipOnStation(List.of(FIRST))));
        defend.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        new CommandPowerSystem(defend.getCommandPowerService(), defend).tick(BattleSimulation.TICK_DT);
        assertEquals(MissionStance.DEFEND, defend.warships().ships().get(0).stance());
    }

    @Test
    void aTenderIsAddedThroughTheTenderSystemUnderTheSameStance() {
        BattleSimulation sim = sim();
        sim.setCompanyStance(MissionStance.DEFEND);
        sim.setCommandPowers(List.of(new ShipOnStation(List.of(FOLD))));
        CommandPowerSystem system = new CommandPowerSystem(sim.getCommandPowerService(), sim);

        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 34, 45);
        system.tick(BattleSimulation.TICK_DT);

        assertEquals(1, sim.tenders().tenders().size());
        Warship hull = sim.tenders().tenders().get(0).ship();
        assertEquals("shepherd", hull.hullId);
        assertEquals(MissionStance.DEFEND, hull.stance());
        assertEquals(ShipOnStation.STATION_SECONDS, hull.stationSecondsRemaining(), 0.001f);
        assertTrue(sim.warships().ships().contains(hull), "its hull is flown like any other");
    }

    @Test
    void theCardNamesTheNextShipAndTheChargesLeft() {
        ShipOnStation power = new ShipOnStation(List.of(FIRST, SECOND));
        assertEquals("Anvil 2/2", power.cardLabel(2));
        assertEquals("Bulwark 1/2", power.cardLabel(1));
        assertEquals("Bulwark 0/2", power.cardLabel(0));
    }

    @Test
    void aPowerWithNoShipIsNotAPower() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShipOnStation(List.of()));
    }
}
