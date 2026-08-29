package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The airfield's berths, and the aircraft standing on them.
 *
 * <p>A based aircraft is an ordinary unit while it is on the ground, which is
 * the point: it can be seen and shot by everything that already shoots units.
 * These exercise the berth state machine directly rather than through a battle.
 */
class AirfieldSystemTest {

    private static final int W = 20;
    private static final int H = 20;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static AirfieldService.Berth berth(BattleSimulation sim, int x, int y) {
        return sim.getAirfieldService().addBerth(
                LandingPad.garrison(x, y, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
    }

    /** A tick puts an aircraft on every berth that has none. */
    @Test
    void aBerthStandsAnAircraftOnItsPad() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        assertEquals(0L, berth.airframeId, "nothing is placed before the first tick");

        new AirfieldSystem(Faction.DEFENDER)
                .tick(1f / 30f, sim, sim.getAirfieldService());

        assertNotEquals(0L, berth.airframeId, "the berth has an aircraft on it");
        assertEquals(UnitType.BASED_AIRCRAFT,
                sim.identity().type(berth.airframeId));
        assertEquals(Faction.DEFENDER, sim.identity().faction(berth.airframeId));
        assertEquals(10, sim.world().cellX(berth.airframeId));
        assertEquals(10, sim.world().cellY(berth.airframeId));
        assertTrue(sim.getAirfieldService().hasAirworthyAirframe());
    }

    /**
     * An airframe destroyed where it stands takes its berth with it.
     *
     * <p>Permanent on purpose: an attacker who spends the effort to burn an
     * aircraft should be able to see that it stays burned, and a field with
     * nothing left on it stops being an air arm.
     */
    @Test
    void anAircraftBurnedOnItsPadIsNotReplaced() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, sim.getAirfieldService());
        long airframe = berth.airframeId;

        sim.applyDamage(airframe, 100_000f, 100_000f);
        system.tick(1f / 30f, sim, sim.getAirfieldService());

        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        assertFalse(sim.getAirfieldService().hasAirworthyAirframe(),
                "a field with nothing left on it cannot fly");

        // And stays that way however long the battle runs.
        for (int i = 0; i < 200; i++) {
            system.tick(1f / 30f, sim, sim.getAirfieldService());
        }
        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        assertEquals(0L, berth.airframeId, "nothing replaces it");
    }

    /** The aircraft is a target, never a shooter. */
    @Test
    void aBasedAircraftNeverFires() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        new AirfieldSystem(Faction.DEFENDER)
                .tick(1f / 30f, sim, sim.getAirfieldService());

        assertEquals(0f, sim.world().attackDamage(berth.airframeId),
                "a parked hull is a target, not a weapon");
        assertEquals(0f, sim.world().attackRange(berth.airframeId));
        assertTrue(UnitType.BASED_AIRCRAFT.isStatic(),
                "it neither paths nor thinks");
    }

    /** A sortie takes an airframe off its pad, and the pad is empty while it is gone. */
    @Test
    void aLaunchedAircraftLeavesAnEmptyPad() {
        BattleSimulation sim = openSim();
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, service);
        long airframe = berth.airframeId;

        float hull = service.launch(berth);
        assertEquals(ShuttleType.AEROSHUTTLE.maxHp, hull, "it flies with the hull it had");
        assertEquals(AirfieldService.BerthState.AWAY, berth.state);
        assertFalse(service.hasAirworthyAirframe(), "there is nothing left to send");

        system.tick(1f / 30f, sim, service);
        assertEquals(0L, berth.airframeId, "the pad is empty while its aircraft is out");
        assertFalse(onMap(sim, airframe),
                "and the unit that stood in for it is off the map");
        assertTrue(sim.getDeathsThisFrame().isEmpty(),
                "taking off is not a death: no wreck, no casualty");
    }

    /**
     * Whether a unit is still on the live roster. Released units keep readable
     * component state — the same as a marine taken aboard a shuttle — so
     * liveness is the roster walk, not an HP read.
     */
    private static boolean onMap(BattleSimulation sim, long unit) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.liveUnitAt(i) == unit) return true;
        }
        return false;
    }

    /** A returned airframe is not available again until it has been turned round. */
    @Test
    void aReturnedAircraftIsUnavailableUntilItIsTurnedRound() {
        BattleSimulation sim = openSim();
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim, 10, 10);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(1f / 30f, sim, service);
        service.launch(berth);

        // Home, shot up.
        float damaged = ShuttleType.AEROSHUTTLE.maxHp * 0.2f;
        service.recover(berth, damaged);
        assertEquals(AirfieldService.BerthState.REFITTING, berth.state);
        assertFalse(service.hasAirworthyAirframe(),
                "a field cannot answer two requests back to back");

        for (int i = 0; i < 30 * (int) AirfieldService.REFIT_SECONDS + 2; i++) {
            system.tick(1f / 30f, sim, service);
        }

        assertEquals(AirfieldService.BerthState.PARKED, berth.state);
        assertTrue(service.hasAirworthyAirframe());
        assertTrue(berth.hullHp > damaged,
                "a turnaround patches the hull: " + berth.hullHp);
        assertTrue(berth.hullHp < ShuttleType.AEROSHUTTLE.maxHp,
                "but a field does not rebuild an airframe: " + berth.hullHp);
        assertNotEquals(0L, berth.airframeId, "and it is standing on the pad again");
    }

    /** A sortie flies from the stand nearest what it is going to. */
    @Test
    void theNearestAirworthyStandSendsTheSortie() {
        BattleSimulation sim = openSim();
        AirfieldService service = sim.getAirfieldService();
        AirfieldService.Berth near = berth(sim, 4, 4);
        AirfieldService.Berth far = berth(sim, 16, 16);

        assertEquals(near, service.nearestAirworthy(2f, 2f));
        assertEquals(far, service.nearestAirworthy(18f, 18f));

        service.launch(near);
        assertEquals(far, service.nearestAirworthy(2f, 2f),
                "with the near stand empty the sortie comes off the far one");
    }
}
