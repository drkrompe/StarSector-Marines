package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective.LiftPoint;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsVaultObjectiveTest {

    private static final float DT = BattleSimulation.TICK_DT;
    /** The landing area: a long way back from the vault at (10, 10). */
    private static final LiftPoint LANDING = new LiftPoint("Landing area", true,
            new int[]{10, 10, 8, 8, 6, 6, 4, 4, 2, 2});
    /** A landing court close by the vault. */
    private static final LiftPoint COURT = new LiftPoint("East court", false,
            new int[]{10, 10, 12, 10, 14, 10});

    @Test
    void powerToTheSealShortensTheBreach() {
        try (BattleSimulation sim = simulation()) {
            RuinsVaultObjective vault = vault(sim, DT * 20f, DT * 2f, List.of(LANDING));
            sim.spawn(new EntitySpec("breacher", Faction.MARINE, UnitType.MARINE, 10, 10));

            vault.tick(sim);
            assertFalse(vault.breached());
            vault.setVaultPowered(true);
            vault.tick(sim);

            assertTrue(vault.breached(), "the powered breach time has already passed");
            assertTrue(vault.alarmActive());
        }
    }

    @Test
    void aForcedSealKeepsTheUnpoweredBreachTimeWhateverPowerFollows() {
        try (BattleSimulation sim = simulation()) {
            RuinsVaultObjective vault = vault(sim, DT * 20f, DT * 2f, List.of(LANDING));
            vault.forceSeal();
            vault.setVaultPowered(true);
            assertEquals(DT * 20f, vault.breachDuration(), 1e-6f);
        }
    }

    @Test
    void theHaulBoardsAtTheLandingAreaAndWins() {
        try (BattleSimulation sim = simulation()) {
            RuinsVaultObjective vault = vault(sim, DT, DT, List.of(LANDING, COURT));
            long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE, UnitType.MARINE, 10, 10));

            vault.tick(sim);
            assertEquals(0, vault.destination(), "an unheld court is no lift point");
            walkTheHaulHome(sim, vault, marine);

            assertTrue(vault.isComplete());
            assertEquals(1, vault.boardedElements());
            assertEquals("Landing area", vault.egressName());
        }
    }

    @Test
    void aHeldCourtNearerTheVaultBecomesTheLiftPoint() {
        try (BattleSimulation sim = simulation()) {
            RuinsVaultObjective vault = vault(sim, RuinsVaultObjective.COURT_HOLD_SECONDS * 2f, DT,
                    List.of(LANDING, COURT));
            long marine = sim.spawn(new EntitySpec("breacher", Faction.MARINE, UnitType.MARINE, 10, 10));
            sim.spawn(new EntitySpec("court guard", Faction.MARINE, UnitType.MARINE, 14, 10));

            while (!vault.breached()) vault.tick(sim);
            assertTrue(vault.liftPointAvailable(1));
            assertEquals(1, vault.destination());
            walkTheHaulHome(sim, vault, marine);

            assertTrue(vault.isComplete());
            assertEquals("East court", vault.egressName());
        }
    }

    @Test
    void anUnattendedHaulUnderHostileControlIsLost() {
        try (BattleSimulation sim = simulation()) {
            RuinsVaultObjective vault = vault(sim, DT, DT, List.of(LANDING));
            long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE, UnitType.MARINE, 10, 10));
            vault.tick(sim);
            sim.world().setCellPos(marine, 18, 18);
            sim.spawn(new EntitySpec("scavenger", Faction.DEFENDER, UnitType.MILITIA,
                    vault.payloadCellX(), vault.payloadCellY()));

            while (!vault.isFailed()) vault.tick(sim);

            assertEquals(ExtractionPayloadObjective.Failure.LOST, vault.failureReason());
            assertEquals(1, vault.lostElements());
        }
    }

    @Test
    void everyLiftPointLeavesFromTheVaultAndOneIsTheLandingArea() {
        try (BattleSimulation sim = simulation()) {
            LiftPoint elsewhere = new LiftPoint("Stray court", false, new int[]{5, 5, 6, 6});
            assertThrows(IllegalArgumentException.class, () -> vault(sim, DT, DT, List.of(COURT)));
            assertThrows(IllegalArgumentException.class, () -> vault(sim, DT, DT, List.of(LANDING, elsewhere)));
            assertThrows(IllegalArgumentException.class, () -> vault(sim, DT, DT * 2f, List.of(LANDING)),
                    "power never slows a breach");
        }
    }

    /** Keeps the escort on the haul until it reaches its lift point, then holds there until it boards. */
    private static void walkTheHaulHome(BattleSimulation sim, RuinsVaultObjective vault, long marine) {
        while (!vault.isComplete() && !vault.isFailed()) {
            sim.world().setCellPos(marine, vault.payloadCellX(), vault.payloadCellY());
            vault.tick(sim);
        }
    }

    private static RuinsVaultObjective vault(BattleSimulation sim, float breach, float poweredBreach,
                                             List<LiftPoint> liftPoints) {
        return new RuinsVaultObjective("RUINS-01", "vault haul", "the vault", sim.getZoneGraph().zoneIdAt(10, 10),
                breach, poweredBreach, liftPoints);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }
}
