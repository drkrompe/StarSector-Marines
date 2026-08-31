package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Riding in a vehicle. The thing that makes this different from delivery is
 * that the squad which gets out is the squad which got in — so most of these
 * are about what survives the ride.
 */
class VehicleTransportTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        for (int y = 10; y <= 50; y++) {
            for (int x = 10; x <= 50; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(60, 60));
    }

    private static long apcAt(BattleSimulation sim, float x, float y) {
        long id = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE,
                VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, 0f);
        return id;
    }

    /** Three marines in one squad, standing on the vehicle. */
    private static int squadBeside(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 3; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, x + i, y)
                    .squad(squadId));
        }
        return squadId;
    }

    @Test
    void aSquadRidesAndGetsOutAgain() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 30.5f, 30.5f);
        int squadId = squadBeside(sim, 30, 30);

        int mounted = sim.transport().mountSquad(apc, squadId);

        assertEquals(3, mounted, "the whole squad boards");
        assertEquals(3, sim.transport().manifest(apc).size());
        assertEquals(1, sim.transport().seatsFree(apc), "an APC seats four");

        // Carry it somewhere else entirely, then set it down.
        sim.convoy().body(apc).teleport(45.5f, 45.5f, 0f);
        int out = sim.transport().dismountAll(apc);

        assertEquals(3, out, "everyone gets out");
        assertTrue(sim.transport().manifest(apc).isEmpty());
        long[] dense = sim.getRoster().denseArray();
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = dense[i];
            if (!sim.squad().hasSquad(unit)) continue;
            float dx = sim.world().x(unit) - 45.5f;
            float dy = sim.world().y(unit) - 45.5f;
            assertTrue(Math.hypot(dx, dy) < 10f,
                    "they got out where the vehicle is, not where they got in");
        }
    }

    @Test
    void aPassengerKeepsItsWeapon() {
        // Mounting narrows what a unit has. Anything carrying authored numbers
        // must survive the ride: removing a component drops its row, so a
        // marine that lost COMBAT would come back with no range and no damage
        // and still look like the same marine to every other assertion here.
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 30.5f, 30.5f);
        int squadId = squadBeside(sim, 30, 30);
        long marine = firstSquadMember(sim, squadId);
        float rangeBefore = sim.world().attackRange(marine);
        float damageBefore = sim.world().attackDamage(marine);
        assertTrue(rangeBefore > 0f, "fixture check: the marine starts armed");

        sim.transport().mountSquad(apc, squadId);
        sim.transport().dismountAll(apc);

        assertEquals(rangeBefore, sim.world().attackRange(marine), 0.001f,
                "a marine that rode in an APC still has its weapon");
        assertEquals(damageBefore, sim.world().attackDamage(marine), 0.001f);
    }

    @Test
    void aRiddenSquadForgetsWhereItWasToldToGo() {
        // A ride across the map invalidates the objective by construction: a
        // squad that kept it would dismount and walk back the way it came.
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 30.5f, 30.5f);
        int squadId = squadBeside(sim, 30, 30);
        sim.getSquad(squadId).assignedObjective =
                ObjectiveAssignment.defendArea(squadId, 12, 12);

        sim.transport().mountSquad(apc, squadId);

        assertNull(sim.getSquad(squadId).assignedObjective,
                "the objective goes with the ride; the command claim does not");
    }

    @Test
    void aSquadTooBigForTheSeatsStaysWhereItIs() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 30.5f, 30.5f);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 6; i++) {
            sim.spawn(new EntitySpec("big" + i, Faction.MARINE, UnitType.MARINE, 30 + i % 3, 30)
                    .squad(squadId));
        }

        int mounted = sim.transport().mountSquad(apc, squadId);

        assertEquals(0, mounted, "six into four does not go, and half a squad is worse than none");
        assertTrue(sim.transport().manifest(apc).isEmpty());
    }

    @Test
    void aWreckedHullLetsOnlySomeOfThemOut() {
        // The same deal a wrecked delivery already gives its passenger count:
        // one or two get out hurt and the rest do not get out. Riders are real
        // units, so getting out is a dismount and not getting out is a death.
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 30.5f, 30.5f);
        int squadId = squadBeside(sim, 30, 30);
        sim.transport().mountSquad(apc, squadId);
        assertEquals(3, sim.transport().manifest(apc).size());

        // Killed the way anything kills it, so this exercises the real
        // destruction path rather than a method the game never calls directly.
        float hull = sim.convoy().structure(apc) + sim.convoy().armor(apc);
        sim.applyDamage(apc, 0L, hull * 4f, 10_000f, 0f);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.transport().manifest(apc).isEmpty(),
                "nobody is still riding a wreck");
        int stillAlive = 0;
        long[] dense = sim.getRoster().denseArray();
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = dense[i];
            if (sim.squad().hasSquad(unit) && sim.squad().squadId(unit) == squadId) stillAlive++;
        }
        assertTrue(stillAlive >= 1 && stillAlive <= 2,
                "one or two bail out of three (got " + stillAlive + ")");
    }

    private static long firstSquadMember(BattleSimulation sim, int squadId) {
        long[] dense = sim.getRoster().denseArray();
        for (int i = 0; i < sim.getRoster().liveCount(); i++) {
            long unit = dense[i];
            if (sim.squad().hasSquad(unit) && sim.squad().squadId(unit) == squadId) return unit;
        }
        return 0L;
    }
}
