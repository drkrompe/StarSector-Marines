package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.MarineSecondary;
import com.dillon.starsectormarines.battle.MarineWeapon;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InfantryUnitPrep#tryOpportunityRocket} — the reactive
 * rocket-on-turret check that runs every tick before a marine's normal
 * action executes, letting them pause and fire a rocket at a turret
 * spotted in passing.
 */
public class InfantryUnitPrepTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    private static Unit rocketeer(BattleSimulation sim, Faction f, int x, int y) {
        Unit u = new Unit("u" + sim.getUnits().size(), f, UnitType.MARINE, x, y);
        u.primaryWeapon = MarineWeapon.PULSE_RIFLE;
        u.attackRange = u.primaryWeapon.range;
        u.secondaryWeapon = MarineSecondary.ROCKET_LAUNCHER;
        u.secondaryAmmo = MarineSecondary.ROCKET_LAUNCHER.startingAmmo;
        sim.addUnit(u);
        return u;
    }

    private static MapTurret turret(BattleSimulation sim, Faction f, TurretKind kind, int x, int y) {
        MapTurret t = new MapTurret("t" + sim.getUnits().size(), f, kind, x, y);
        sim.addUnit(t);
        return t;
    }

    @Test
    public void opportunityRocketStartsAimWhenTurretInRangeWithLos() {
        BattleSimulation sim = openArena(50, 10);
        Unit marine = rocketeer(sim, Faction.MARINE, 5, 5);
        // Past pulse-rifle range (24), well inside rocket range (32).
        MapTurret turret = turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        boolean started = InfantryUnitPrep.tryOpportunityRocket(marine, sim);
        assertTrue(started, "marine in rocket range with LOS should start aim");
        assertEquals(MarineSecondary.ROCKET_LAUNCHER.aimDuration,
                marine.secondaryActionTimer, 0.001f);
        assertEquals(turret.entityId, marine.secondaryAimTargetId);
    }

    @Test
    public void opportunityRocketNoOpsWithNoAmmo() {
        BattleSimulation sim = openArena(50, 10);
        Unit marine = rocketeer(sim, Faction.MARINE, 5, 5);
        marine.secondaryAmmo = 0;
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim));
        assertEquals(0f, marine.secondaryActionTimer, 0.001f);
    }

    @Test
    public void opportunityRocketNoOpsWhenCooldownActive() {
        BattleSimulation sim = openArena(50, 10);
        Unit marine = rocketeer(sim, Faction.MARINE, 5, 5);
        marine.secondaryCooldownTimer = 1.0f;
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim));
    }

    @Test
    public void opportunityRocketNoOpsWhenTurretBeyondRocketRange() {
        BattleSimulation sim = openArena(80, 10);
        Unit marine = rocketeer(sim, Faction.MARINE, 5, 5);
        // 50 cells away — well past rocket range (32).
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 55, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim));
    }

    @Test
    public void opportunityRocketNoOpsWhenLosBlocked() {
        BattleSimulation sim = openArena(50, 10);
        NavigationGrid grid = sim.getGrid();
        // Wall column between marine and turret.
        for (int y = 0; y < 10; y++) grid.setWalkable(15, y, false);
        Unit marine = rocketeer(sim, Faction.MARINE, 5, 5);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marine, sim),
                "LOS-blocked turret must not trigger opportunity fire");
    }

    @Test
    public void opportunityRocketRespectsSquadCoordination() {
        // Hephaestus needs 2 rockets — first two marines commit, third holds.
        BattleSimulation sim = openArena(50, 10);
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Unit marineA = rocketeer(sim, Faction.MARINE, 5, 5);
        marineA.squadId = squadId;
        Unit marineB = rocketeer(sim, Faction.MARINE, 5, 6);
        marineB.squadId = squadId;
        Unit marineC = rocketeer(sim, Faction.MARINE, 5, 4);
        marineC.squadId = squadId;
        turret(sim, Faction.DEFENDER, TurretKind.HEPHAESTUS, 28, 5);

        assertTrue(InfantryUnitPrep.tryOpportunityRocket(marineA, sim));
        assertTrue(InfantryUnitPrep.tryOpportunityRocket(marineB, sim),
                "second squadmate joins when one inbound rocket can't kill the turret");
        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marineC, sim),
                "third squadmate must hold fire — projected damage already exceeds turret HP");
    }

    @Test
    public void opportunityRocketBlocksSecondShotOnVulcan() {
        // The common case: Vulcan dies to one rocket. Once marineA commits,
        // marineB must not also fire.
        BattleSimulation sim = openArena(50, 10);
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Unit marineA = rocketeer(sim, Faction.MARINE, 5, 5);
        marineA.squadId = squadId;
        Unit marineB = rocketeer(sim, Faction.MARINE, 5, 6);
        marineB.squadId = squadId;
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertTrue(InfantryUnitPrep.tryOpportunityRocket(marineA, sim));
        assertFalse(InfantryUnitPrep.tryOpportunityRocket(marineB, sim),
                "Vulcan dies to one rocket — second squadmate must skip");
    }

    @Test
    public void opportunityRocketIgnoresFriendlyTurret() {
        // Defender turret near a defender unit (e.g. a rocketeer enemy).
        // Sanity: the scan filters by enemy faction, not just "is a turret."
        BattleSimulation sim = openArena(50, 10);
        Unit defenderRocketeer = rocketeer(sim, Faction.DEFENDER, 5, 5);
        turret(sim, Faction.DEFENDER, TurretKind.VULCAN, 28, 5);

        assertFalse(InfantryUnitPrep.tryOpportunityRocket(defenderRocketeer, sim),
                "friendly turret must not be a rocket target");
    }
}
