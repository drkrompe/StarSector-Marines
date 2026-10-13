package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.OrbitalLift;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The commander's own call for a mech lance: its allowance, its claim and where it lands. */
class MechDropServiceTest {

    private static final int W = 80;
    private static final int H = 60;
    private static final List<MechVariant> LANCE =
            List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.HOUND, MechVariant.SIROCCO);
    private static final SquadCommandClaim CLAIM =
            SquadCommandClaim.mission("conquest-attacker", "commander-called mech lance");

    private static BattleSimulation openSim(MechDropService service) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMechDrops(service);
        // Keep both sides present so the battle never reads as decided while a carrier is inbound.
        sim.spawn(new EntitySpec("far-marine", Faction.MARINE, UnitType.MARINE, 2, 2)
                .health(100_000f).attackDamage(0f).moveSpeed(0f));
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MILITIA, 70, 50)
                .health(100_000f).attackDamage(0f));
        return sim;
    }

    private static MechDropService marines(int lances) {
        return new MechDropService(MechDropDeclaration.NONE.with(Faction.MARINE,
                new MechDropAllowance(lances, LANCE)));
    }

    @Test
    void anAllowanceIsSpentOneLanceAtATimeAndThenRefuses() {
        MechDropService service = marines(2);
        BattleSimulation sim = openSim(service);

        assertEquals(2, service.lancesRemaining(Faction.MARINE));
        assertEquals(MechDropService.Outcome.COMMITTED,
                service.call(sim, Faction.MARINE, "conquest-attacker", CLAIM, 20, 30));
        assertEquals(1, service.lancesRemaining(Faction.MARINE));
        assertEquals(MechDropService.Outcome.COMMITTED,
                service.call(sim, Faction.MARINE, "conquest-attacker", CLAIM, 20, 30));
        assertEquals(0, service.lancesRemaining(Faction.MARINE));
        int carriers = sim.getAirEntityIds().length;

        assertEquals(MechDropService.Outcome.NO_ALLOWANCE,
                service.call(sim, Faction.MARINE, "conquest-attacker", CLAIM, 20, 30));
        assertEquals(carriers, sim.getAirEntityIds().length, "a refused call puts nothing in the air");
        assertEquals(2, service.drops().size());
    }

    @Test
    void aSideWithNoDeclaredAllowanceCannotCallAndTheSharedNoneStaysInert() {
        BattleSimulation sim = openSim(marines(1));
        assertEquals(MechDropService.Outcome.NO_ALLOWANCE,
                sim.mechDrops().call(sim, Faction.DEFENDER, "d", CLAIM, 20, 30));

        BattleSimulation bare = openSim(null);
        assertSame(MechDropService.NONE, bare.mechDrops());
        assertEquals(MechDropService.Outcome.NO_ALLOWANCE,
                bare.callMechDrop(Faction.MARINE, CLAIM, 20, 30));
        assertEquals(0, bare.getAirEntityIds().length);
        MechDropService.NONE.installScriptedCaller(Faction.MARINE, CLAIM, () -> new int[]{1, 1});
        MechDropService.NONE.advance(bare);
        assertEquals(0, bare.getAirEntityIds().length);
    }

    @Test
    void theLedgerIsPerSideSoADefenderCommanderCouldCallToo() {
        MechDropService service = new MechDropService(MechDropDeclaration.NONE
                .with(Faction.MARINE, new MechDropAllowance(1, LANCE))
                .with(Faction.DEFENDER, new MechDropAllowance(1, List.of(MechVariant.HOUND))));
        BattleSimulation sim = openSim(service);

        assertEquals(MechDropService.Outcome.COMMITTED,
                service.call(sim, Faction.DEFENDER, "conquest-defender", CLAIM, 60, 30));
        assertEquals(0, service.lancesRemaining(Faction.DEFENDER));
        assertEquals(1, service.lancesRemaining(Faction.MARINE), "one side's call never spends the other's");
        long carrier = service.drops().get(0).carrier();
        assertEquals(Faction.DEFENDER, sim.identity().faction(carrier));
        assertEquals(1, sim.world().mission(carrier).mechVariants.length);
    }

    @Test
    void theCarrierIsTheSharedMechSupportDeliveryClaimedForTheCaller() {
        MechDropService service = marines(1);
        BattleSimulation sim = openSim(service);

        service.call(sim, Faction.MARINE, "conquest-attacker", CLAIM, 20, 30);

        ShuttleMission mission = sim.world().mission(service.drops().get(0).carrier());
        assertSame(MechSupportPayload.INSTANCE, mission.payload);
        assertSame(CLAIM, mission.commandClaim, "the lance belongs to the commander that asked, not the player");
        assertEquals(1, mission.totalCycles);
        assertEquals(4, mission.marinesRemaining);
        assertEquals(LANCE, List.of(mission.mechVariants));
        assertEquals(Faction.MARINE, sim.identity().faction(service.drops().get(0).carrier()));
    }

    @Test
    void theLandingZoneIsTheNearestViableCellToTheHint() {
        MechDropService service = marines(1);
        BattleSimulation sim = openSim(service);
        // A building fills the hint and everything within a few cells of it.
        for (int y = 25; y <= 35; y++) {
            for (int x = 15; x <= 25; x++) sim.getTopology().setBuildingId(x, y, 1);
        }

        int[] lz = service.landingZone(Faction.MARINE, sim, 20, 30);
        assertNotNull(lz);
        assertTrue(Math.abs(lz[0] - 20) + Math.abs(lz[1] - 30) <= MechDropService.LZ_SEARCH_RADIUS);
        assertTrue(sim.getTopology().getBuildingId(lz[0], lz[1]) == 0, "never put down inside a building");

        service.call(sim, Faction.MARINE, "conquest-attacker", CLAIM, 20, 30);
        assertEquals(lz[0], service.drops().get(0).lzCellX());
        assertEquals(lz[1], service.drops().get(0).lzCellY());
    }

    @Test
    void noViableGroundNearTheHintRefusesWithoutSpendingAnything() {
        MechDropService service = marines(1);
        BattleSimulation sim = openSim(service);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) sim.getTopology().setBuildingId(x, y, 1);
        }

        assertNull(service.landingZone(Faction.MARINE, sim, 20, 30));
        assertEquals(MechDropService.Outcome.NO_LANDING_ZONE,
                service.call(sim, Faction.MARINE, "conquest-attacker", CLAIM, 20, 30));
        assertEquals(1, service.lancesRemaining(Faction.MARINE), "an undeliverable call is not paid for");
        assertEquals(0, sim.getAirEntityIds().length);
        assertEquals(MechDropService.Outcome.NO_LANDING_ZONE,
                service.call(sim, Faction.MARINE, "x", CLAIM, -5, 900));
    }

    @Test
    void aMarineLanceDescendsFromOrbitAndAnotherSideCrossesTheEdge() {
        MechDropService service = new MechDropService(MechDropDeclaration.NONE
                .with(Faction.MARINE, new MechDropAllowance(1, LANCE))
                .with(Faction.DEFENDER, new MechDropAllowance(1, LANCE)));
        BattleSimulation sim = openSim(service);

        service.call(sim, Faction.MARINE, "m", CLAIM, 30, 30);
        service.call(sim, Faction.DEFENDER, "d", CLAIM, 30, 30);
        ShuttleMission marine = sim.world().mission(service.drops().get(0).carrier());
        ShuttleMission defender = sim.world().mission(service.drops().get(1).carrier());

        if (OrbitalLift.enabled()) {
            float lzX = service.drops().get(0).lzCellX() + 0.5f;
            float lzY = service.drops().get(0).lzCellY() + 0.5f;
            assertEquals(OrbitalLift.DESCENT_CELLS,
                    (float) Math.hypot(marine.entryX - lzX, marine.entryY - lzY), 0.01f);
            assertArrayEquals(new float[]{marine.entryX, marine.entryY},
                    new float[]{marine.exitX, marine.exitY}, "the descent is left where it was entered");
        }
        boolean offMap = defender.entryX < 0 || defender.entryX > W || defender.entryY < 0 || defender.entryY > H;
        assertTrue(offMap, "a planetary delivery crosses the map edge");
    }

    @Test
    void aScriptedCallFiresOnceAtItsTickForTheNamedClaimAtTheDropZone() {
        MechDropService service = new MechDropService(MechDropDeclaration.NONE.with(Faction.MARINE,
                new MechDropAllowance(2, LANCE).calledAtTick(5)));
        BattleSimulation sim = openSim(service);
        service.installScriptedCaller(Faction.MARINE, CLAIM, () -> new int[]{20, 30});

        for (int i = 0; i < 4; i++) sim.advance(BattleSimulation.TICK_DT);
        assertEquals(0, service.drops().size(), "not before its tick");
        for (int i = 0; i < 10; i++) sim.advance(BattleSimulation.TICK_DT);
        assertEquals(1, service.drops().size(), "once, though a second lance remains");
        assertEquals(1, service.lancesRemaining(Faction.MARINE));
        assertSame(CLAIM, sim.world().mission(service.drops().get(0).carrier()).commandClaim);
    }

    @Test
    void anAllowanceNormalisesItsLanceAndAZeroCountIsNone() {
        assertTrue(new MechDropAllowance(0, LANCE).isNone());
        assertTrue(new MechDropAllowance(3, List.of()).isNone());
        assertEquals(MechSupport.LANCE_SIZE, MechDropAllowance.MAX_LANCE_SIZE);
        MechDropAllowance five = new MechDropAllowance(1, List.of(MechVariant.HOUND, MechVariant.HOUND,
                MechVariant.HOUND, MechVariant.HOUND, MechVariant.BULWARK));
        assertEquals(MechDropAllowance.MAX_LANCE_SIZE, five.lance().size());
        assertTrue(MechDropDeclaration.NONE.with(Faction.MARINE, MechDropAllowance.NONE).isNone());
    }
}
