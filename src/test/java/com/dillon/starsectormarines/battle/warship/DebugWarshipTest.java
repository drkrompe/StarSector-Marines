package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugWarshipTest {

    private static final float LENGTH = 40f;

    @Test
    void marinesStartOnTheWestEdgeFacingEastAndStationTwentyCellsIn() {
        DebugWarship.Placement p = DebugWarship.place(Faction.MARINE, TraversalAxis.WEST_TO_EAST, 560, 336, LENGTH);
        assertEquals(20f, p.x(), 1e-4f);
        assertEquals(168f, p.y(), 1e-4f);
        assertEquals(-90f, p.facingDegrees(), 1e-4f);
        assertEquals(40f, p.stationX(), 1e-4f);
        assertEquals(168f, p.stationY(), 1e-4f);
    }

    @Test
    void defendersStartOnTheEastEdgeFacingWest() {
        DebugWarship.Placement p = DebugWarship.place(Faction.DEFENDER, TraversalAxis.WEST_TO_EAST, 560, 336, LENGTH);
        assertEquals(540f, p.x(), 1e-4f);
        assertEquals(90f, p.facingDegrees(), 1e-4f);
        assertEquals(520f, p.stationX(), 1e-4f);
    }

    @Test
    void southToNorthPutsMarinesAtTheSouthEdgeAndDefendersAtTheNorth() {
        DebugWarship.Placement marine = DebugWarship.place(Faction.MARINE, TraversalAxis.SOUTH_TO_NORTH, 200, 300, LENGTH);
        assertEquals(100f, marine.x(), 1e-4f);
        assertEquals(20f, marine.y(), 1e-4f);
        assertEquals(0f, marine.facingDegrees(), 1e-4f);
        assertEquals(40f, marine.stationY(), 1e-4f);
        DebugWarship.Placement defender = DebugWarship.place(Faction.DEFENDER, TraversalAxis.SOUTH_TO_NORTH, 200, 300, LENGTH);
        assertEquals(280f, defender.y(), 1e-4f);
        assertEquals(180f, defender.facingDegrees(), 1e-4f);
        assertEquals(260f, defender.stationY(), 1e-4f);
    }

    @Test
    void theSelectorCyclesOffOnslaughtParagonShepherdAndBack() {
        DebugWarship.Hull h = DebugWarship.Hull.OFF;
        assertEquals(DebugWarship.Hull.ONSLAUGHT, h.cycle(1));
        assertEquals(DebugWarship.Hull.SHEPHERD, h.cycle(-1));
        assertEquals(DebugWarship.Hull.SHEPHERD, DebugWarship.Hull.PARAGON.cycle(1));
        assertEquals(DebugWarship.Hull.OFF, DebugWarship.Hull.SHEPHERD.cycle(1));
        assertFalse(DebugWarship.Spec.OFF.active());
    }

    @Test
    void offAddsNothingAndADirectedShipFiresOnTheDefendersWithinTheBudget() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(160, 90).missionCompletion(false)
                .unit("d1", Faction.DEFENDER, UnitType.MARINE, 70, 38)
                .unit("d2", Faction.DEFENDER, UnitType.MARINE, 71, 40)
                .unit("d3", Faction.DEFENDER, UnitType.MARINE, 70, 42)
                .build();
        BattleSimulation sim = world.sim();

        assertNull(DebugWarship.install(sim, DebugWarship.Spec.OFF));
        assertTrue(sim.warships().ships().isEmpty());

        Warship ship = DebugWarship.install(sim,
                new DebugWarship.Spec(DebugWarship.Hull.ONSLAUGHT, Faction.MARINE, DebugWarship.Mode.DIRECTOR));
        assertNotNull(ship);
        assertTrue(ship.isDirected());
        assertEquals(-90f, ship.facingDegrees, 1e-4f);

        SceneRun.of(world).run(900);
        int fired = ship.mounts().stream().mapToInt(m -> m.shotsFired).sum();
        assertTrue(fired > 0, "a directed ship fires on defenders in reach: " + DebugWarship.statusLine(ship));
        assertTrue(DebugWarship.statusLine(ship).contains("rounds " + fired), DebugWarship.statusLine(ship));
    }

    @Test
    void fluxIsOnByDefaultAtTheBrawlersShareAndTheSpecOrToggleTurnsItOffAndTheStatusLineReadsIt() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(160, 90).missionCompletion(false)
                .unit("d1", Faction.DEFENDER, UnitType.MARINE, 70, 38).build();
        Warship plain = DebugWarship.install(world.sim(),
                new DebugWarship.Spec(DebugWarship.Hull.ONSLAUGHT, Faction.MARINE, DebugWarship.Mode.MANUAL));
        assertTrue(plain.fluxLimited());
        assertEquals(Warship.BRAWLER_FLUX_SCALE, plain.fluxScale());
        assertTrue(DebugWarship.statusLine(plain).contains("FIRING"), DebugWarship.statusLine(plain));
        DebugWarship.toggleFlux(plain);
        assertFalse(plain.fluxLimited());
        assertTrue(DebugWarship.statusLine(plain).contains("flux off"), DebugWarship.statusLine(plain));
        DebugWarship.toggleFlux(plain);
        assertTrue(plain.fluxLimited());

        Warship off = DebugWarship.install(world.sim(), new DebugWarship.Spec(
                DebugWarship.Hull.PARAGON, Faction.MARINE, DebugWarship.Mode.MANUAL).withOtherFlux());
        assertFalse(off.fluxLimited());
    }

    @Test
    void aManualShipHoldsFireUntilToldToFireAtACell() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(160, 90).missionCompletion(false)
                .unit("d1", Faction.DEFENDER, UnitType.MARINE, 70, 38)
                .unit("d2", Faction.DEFENDER, UnitType.MARINE, 71, 40)
                .build();
        Warship ship = DebugWarship.install(world.sim(),
                new DebugWarship.Spec(DebugWarship.Hull.PARAGON, Faction.MARINE, DebugWarship.Mode.MANUAL));
        assertFalse(ship.isDirected());

        SceneRun run = SceneRun.of(world);
        run.run(300);
        assertEquals(0, ship.mounts().stream().mapToInt(m -> m.shotsFired).sum());

        DebugWarship.fireAt(ship, 70f, 39f);
        run.run(900);
        assertTrue(ship.mounts().stream().mapToInt(m -> m.shotsFired).sum() > 0, DebugWarship.statusLine(ship));

        DebugWarship.holdFire(ship);
        assertTrue(DebugWarship.statusLine(ship).contains("HoldFire"));
    }

    @Test
    void aSinglePointIsAStationAndSeveralAreALoopedPatrol() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 20f, 80f, -90f);

        DebugWarship.patrol(ship, List.of(new float[] {60f, 40f}));
        assertEquals(-1, ship.routeIndex());
        assertFalse(ship.isPassingThrough());

        DebugWarship.patrol(ship, List.of(new float[] {60f, 40f}, new float[] {90f, 40f}, new float[] {90f, 70f}));
        assertEquals(0, ship.routeIndex());
        assertTrue(ship.isPassingThrough());

        DebugWarship.goTo(ship, 30f, 30f);
        assertEquals(-1, ship.routeIndex());
    }

    @Test
    void aDirectedShipCanTargetAnEmplacementThatHasNoMovement() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(160, 90).missionCompletion(false)
                .unit("t1", Faction.DEFENDER, UnitType.TURRET, 70, 40)
                .unit("d1", Faction.DEFENDER, UnitType.MARINE, 150, 80)
                .build();
        Warship ship = DebugWarship.install(world.sim(),
                new DebugWarship.Spec(DebugWarship.Hull.ONSLAUGHT, Faction.MARINE, DebugWarship.Mode.DIRECTOR));
        SceneRun.of(world).run(600);
        int fired = ship.mounts().stream().mapToInt(m -> m.shotsFired).sum();
        assertTrue(fired > 0, "it fires without reading a velocity the turret does not have");
    }

    @Test
    void aSaturatingAreaIsFiredIntoEvenWithNobodyInIt() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld world = SceneBuilder.openGround(160, 90).missionCompletion(false)
                .unit("d1", Faction.DEFENDER, UnitType.MARINE, 150, 80)
                .build();
        Warship ship = DebugWarship.install(world.sim(),
                new DebugWarship.Spec(DebugWarship.Hull.ONSLAUGHT, Faction.MARINE, DebugWarship.Mode.MANUAL));
        DebugWarship.fireAt(ship, 70f, 40f);
        SceneRun.of(world).run(600);
        int fired = ship.mounts().stream().filter(m -> !m.pointDefence()).mapToInt(m -> m.shotsFired).sum();
        assertTrue(fired > 0, "an empty circle laid by hand is worked over: " + DebugWarship.statusLine(ship));
    }
}
