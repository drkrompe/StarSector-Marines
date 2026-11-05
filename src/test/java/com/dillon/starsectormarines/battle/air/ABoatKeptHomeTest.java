package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.BoatGun;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A boat on a sortie wears her berth's guns, and one the yard wants comes straight home and stays. */
class ABoatKeptHomeTest {

    private static final float TICK = 1f / 20f;

    @AfterEach
    void forgetTheSpec() {
        TurretSlotResolver.useHullSpecs(null);
    }

    @Test
    void aSortieCarriesTheGunsHerBerthWasRefittedWith() throws Exception {
        JSONObject spec = new JSONObject("{\"center\":[20,20],\"weaponSlots\":["
                + "{\"type\":\"BALLISTIC\",\"mount\":\"TURRET\",\"angle\":90,\"arc\":150,\"locations\":[4,6]}]}");
        TurretSlotResolver.useHullSpecs(hullId -> spec);
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = oneBoat(sim);
        String gun = BoatGun.ARBALEST.structureId();
        berth.doorGuns = new String[]{gun};

        BoatSortieSystem.send(sim, sim.getAirfieldService(), berth);

        MountedTurret[] mounts = sim.getAirTurretMounts(sim.getAirEntityIds()[0]);
        assertEquals(1, mounts.length);
        assertEquals(gun, mounts[0].mount.structureId);
        assertEquals(150f, mounts[0].mount.arcDeg, "a company boat's gun keeps its slot's arc in the air");
    }

    @Test
    void aBoatCalledHomeStandsOnHerBerthAtOnceWithTheHullSheLeftWith() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = oneBoat(sim);
        sim.advance(TICK);
        berth.hullHp = 37f;
        sim.world().setHp(berth.airframeId, 37f);
        BoatSortieSystem.send(sim, sim.getAirfieldService(), berth);
        sim.advance(TICK);

        sim.callHome(berth);
        sim.advance(TICK);

        assertSame(AirfieldService.BerthState.REFITTING, berth.state);
        assertEquals(0, sim.getAirEntityIds().length, "she is not still flying as well");
        assertNotEquals(0L, berth.airframeId, "she stands on the berth");
        assertTrue(sim.world().isAlive(berth.airframeId));
        assertEquals(37f, sim.world().hp(berth.airframeId), 0.001f);
    }

    @Test
    void aBoatCalledHomeTheTickSheLaunchedLeavesOneHullOnTheStand() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = oneBoat(sim);
        sim.advance(TICK);
        long standing = berth.airframeId;
        BoatSortieSystem.send(sim, sim.getAirfieldService(), berth);

        sim.callHome(berth);
        sim.advance(TICK);

        assertFalse(sim.world().isAlive(standing), "the stand-in she launched from came off the field");
        assertNotEquals(standing, berth.airframeId);
    }

    @Test
    void aBoatKeptHomeIsNotOffered() {
        AirfieldService.Berth berth = oneBoat(openSim());
        assertTrue(berth.airworthy());
        berth.keptHome = true;
        assertFalse(berth.airworthy());
    }

    private static AirfieldService.Berth oneBoat(BattleSimulation sim) {
        AirfieldService bay = sim.getAirfieldService();
        bay.setOwner(Faction.MARINE);
        return bay.addBayBerth(new Gantry(20, 30, 3, 2, Gantry.Facing.SOUTH, Gantry.Holds.BOAT),
                ShuttleType.AEROSHUTTLE, new float[]{20.5f, 4f});
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(40, 40));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }
}
