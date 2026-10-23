package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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

/**
 * A squad aboard a vehicle is somewhere — where its vehicle is — but it is not
 * on the ground. Mounting strips a passenger's {@code POSITION}, and every pass
 * that walked the roster and read a position used to throw on one. These ask
 * the two questions apart: a position read answers the carrier's, and the
 * passes about standing on the ground (splash, capture, sight) leave a
 * passenger out.
 */
class MountedSquadTest {

    private static final float APC_X = 30.5f;
    private static final float APC_Y = 30.5f;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        for (int y = 0; y < 60; y++) {
            for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(60, 60), 20261022L);
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long apcAt(BattleSimulation sim, float x, float y) {
        long id = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE,
                VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, 0f);
        return id;
    }

    /** Three marines in one squad, standing at the hull, put aboard it. */
    private static List<Long> mountedSquad(BattleSimulation sim, long apc) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 3; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, 30 + i, 30)
                    .squad(squadId).moveSpeed(0f));
        }
        assertEquals(3, sim.transport().mountSquad(apc, squadId), "fixture: the squad boards");
        return sim.transport().manifest(apc);
    }

    @Test
    void aPassengerIsWhereItsVehicleIs() {
        try (BattleSimulation sim = arena()) {
            long apc = apcAt(sim, APC_X, APC_Y);
            List<Long> riders = mountedSquad(sim, apc);
            sim.convoy().body(apc).teleport(44.5f, 12.5f, 0f);

            for (long rider : riders) {
                assertFalse(sim.world().hasPosition(rider), "a passenger has no cell of its own");
                assertEquals(44.5f, sim.world().x(rider), 1e-4f);
                assertEquals(12.5f, sim.world().y(rider), 1e-4f);
                assertEquals(44, sim.world().cellX(rider));
                assertEquals(44.5f, sim.world().renderX(rider), 1e-4f,
                        "drawn and heard where the vehicle is, not at the map origin");
            }
        }
    }

    @Test
    void aBlastAtTheHullHitsTheVehicleAndNotThePeopleInside() {
        try (BattleSimulation sim = arena()) {
            long apc = apcAt(sim, APC_X, APC_Y);
            List<Long> riders = mountedSquad(sim, apc);
            float hullBefore = sim.convoy().structure(apc) + sim.convoy().armor(apc);

            sim.detonateNow(new PendingDetonation(0L, APC_X, APC_Y, 0f,
                    3f, 40f, 30f, 0, Faction.DEFENDER, false));

            for (long rider : riders) {
                assertEquals(sim.world().maxHp(rider), sim.world().hp(rider), 1e-4f,
                        "the hull stands between the blast and its passengers");
            }
            assertTrue(sim.convoy().structure(apc) + sim.convoy().armor(apc) < hullBefore,
                    "the vehicle itself takes the blast");
        }
    }

    @Test
    void aSquadParkedOnACompoundInItsCarrierDoesNotTakeIt() {
        try (BattleSimulation sim = arena()) {
            long apc = apcAt(sim, APC_X, APC_Y);
            mountedSquad(sim, apc);
            CompoundService service = new CompoundService();
            TacticalNode node = new TacticalNode(TacticalNode.Kind.BARRACKS, 30, 30,
                    28, 28, 32, 32, Faction.DEFENDER, 50, 4);
            service.register(node);

            new CompoundCaptureSystem().tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);

            assertEquals(CompoundService.CompoundState.DEFENDER_HELD, service.getRecord(node).state,
                    "nobody has set foot on it");
        }
    }

    @Test
    void aSquadThatRodeSeesAgainOnceSetDown() {
        try (BattleSimulation sim = arena()) {
            long apc = apcAt(sim, APC_X, APC_Y);
            mountedSquad(sim, apc);
            settle(sim);

            // Drive across the map, put the squad down, and send the empty hull
            // back, so whatever is revealed where they stand is their own sight.
            sim.convoy().body(apc).teleport(8.5f, 50.5f, 0f);
            settle(sim);
            assertEquals(3, sim.transport().dismountAll(apc));
            sim.convoy().body(apc).teleport(52.5f, 8.5f, 0f);
            settle(sim);

            assertTrue(sim.getFogOfWar().isCellRevealed(8, 50),
                    "the squad that got out casts sight again where it stands");
        }
    }

    @Test
    void aBattleTicksWithASquadAboardAndShellsLandingAroundIt() {
        try (BattleSimulation sim = arena()) {
            long apc = apcAt(sim, APC_X, APC_Y);
            List<Long> riders = mountedSquad(sim, apc);
            sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MILITIA, 36, 30).moveSpeed(0f));

            for (int t = 0; t < 30; t++) {
                if (t % 5 == 0) {
                    sim.detonateNow(new PendingDetonation(0L, APC_X + 1f, APC_Y, 0f,
                            2f, 1f, 1f, 0, Faction.DEFENDER, false));
                }
                sim.advance(BattleSimulation.TICK_DT);
            }

            for (long rider : riders) assertTrue(sim.isRiding(rider), "still aboard");
        }
    }

    private static void settle(BattleSimulation sim) {
        for (int i = 0; i < 9; i++) sim.advance(BattleSimulation.TICK_DT);
    }
}
