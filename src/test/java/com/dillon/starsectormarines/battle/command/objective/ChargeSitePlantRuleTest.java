package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plant is the kit squad's job: any of them near the charge works it, while the squad still holds the kit. */
class ChargeSitePlantRuleTest {
    private static final int W = 20, H = 12, SITE_X = 5, SITE_Y = 5;

    private static BattleSimulation open() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static long marine(BattleSimulation sim, int x, int y, int squad) {
        long id = sim.spawn(new EntitySpec("m" + x + "-" + y, Faction.MARINE, UnitType.MARINE, x, y));
        sim.squad().assignSquad(id, squad);
        return id;
    }

    @Test void aSquadMateBesideTheChargeWorksItWhileTheCarrierIsAway() {
        BattleSimulation sim = open();
        ChargeSiteObjective site = new ChargeSiteObjective(SITE_X, SITE_Y, 5f, "SAB-01", "site");
        sim.addObjective(site);
        long carrier = sim.spawn(new EntitySpec("carrier", Faction.MARINE, UnitType.MARINE, 15, SITE_Y)
                .role(UnitRole.PLANTER).assignedObjective(site));
        int squad = sim.mintSquad(Faction.MARINE, carrier);
        sim.squad().assignSquad(carrier, squad);
        marine(sim, SITE_X + 2, SITE_Y, squad);
        site.tick(sim);
        assertTrue(site.planterOnSite());
        assertEquals(BattleSimulation.TICK_DT, site.progress(), 1e-6f);
    }

    @Test void nobodyFromTheKitSquadInReachResetsAndAStrangerNeverPlants() {
        BattleSimulation sim = open();
        ChargeSiteObjective site = new ChargeSiteObjective(SITE_X, SITE_Y, 5f, "SAB-01", "site");
        sim.addObjective(site);
        long carrier = sim.spawn(new EntitySpec("carrier", Faction.MARINE, UnitType.MARINE, 15, SITE_Y)
                .role(UnitRole.PLANTER).assignedObjective(site));
        int squad = sim.mintSquad(Faction.MARINE, carrier);
        sim.squad().assignSquad(carrier, squad);
        long mate = marine(sim, SITE_X, SITE_Y + 1, squad);
        site.tick(sim);
        assertTrue(site.progress() > 0f);
        // Another squad's marine on the charge does nothing; with the mate gone the channel resets.
        long stranger = sim.spawn(new EntitySpec("stranger", Faction.MARINE, UnitType.MARINE, SITE_X, SITE_Y));
        sim.squad().assignSquad(stranger, sim.mintSquad(Faction.MARINE, stranger));
        sim.applyDamage(mate, 100_000f, 100f, 0f);
        site.tick(sim);
        assertFalse(site.planterOnSite());
        assertEquals(0f, site.progress());
    }

    @Test void withTheKitDroppedTheSquadNoLongerPlants() {
        BattleSimulation sim = open();
        ChargeSiteObjective site = new ChargeSiteObjective(SITE_X, SITE_Y, 5f, "SAB-01", "site");
        sim.addObjective(site);
        long carrier = sim.spawn(new EntitySpec("carrier", Faction.MARINE, UnitType.MARINE, 15, SITE_Y)
                .role(UnitRole.PLANTER).assignedObjective(site));
        int squad = sim.mintSquad(Faction.MARINE, carrier);
        sim.squad().assignSquad(carrier, squad);
        marine(sim, SITE_X, SITE_Y, squad);
        sim.applyDamage(carrier, 100_000f, 100f, 0f);
        site.tick(sim);
        assertFalse(site.planterOnSite());
    }

    @Test void aWallBetweenTheMateAndTheChargeKeepsItFromWorkingIt() {
        BattleSimulation sim = open();
        for (int y = 0; y < H; y++) sim.getGrid().setWalkable(SITE_X + 1, y, false);
        ChargeSiteObjective site = new ChargeSiteObjective(SITE_X, SITE_Y, 5f, "SAB-01", "site");
        sim.addObjective(site);
        long carrier = sim.spawn(new EntitySpec("carrier", Faction.MARINE, UnitType.MARINE, 15, SITE_Y)
                .role(UnitRole.PLANTER).assignedObjective(site));
        int squad = sim.mintSquad(Faction.MARINE, carrier);
        sim.squad().assignSquad(carrier, squad);
        marine(sim, SITE_X + 2, SITE_Y, squad);
        site.tick(sim);
        assertFalse(site.planterOnSite());
    }
}
