package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.ClearanceRoutePlanner;
import com.dillon.starsectormarines.battle.nav.ContinuousRoute;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.SeparationSystem;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousMechMotionTest {
    private static final float DT = 1f / 30f;

    @Test
    void subcellBearingTurnsHipsAndMovesWithoutRoundingToTheProjectedCell() {
        Fixture f = new Fixture(true);
        long mech = f.mech(MechVariant.BULWARK, 3.2f, 3.2f);
        f.install(mech, 3, 3, new ClearanceRoutePlanner.Point(3.2f, 3.2f),
                new ClearanceRoutePlanner.Point(3.45f, 3.3f));
        f.face(mech, 180f);
        assertEquals(MovementService.MotionResult.HELD_FOR_TURN, f.move(mech, DT));
        assertEquals(3.2f, f.world.x(mech));
        assertFalse(f.movement.atCell(mech, 3, 3), "a nearby requested center is not a completed route");
        for (int tick = 0; tick < 180; tick++) f.hips.tick(DT);
        float desired = MechLocomotion.continuousFacing(0.25f, 0.1f);
        assertEquals(desired, f.facing(mech), 0.001f);
        assertNotEquals(MechLocomotion.desiredFacing(0.25f, 0.1f), desired);
        MechFacingIntent.Point horizon = MechFacingIntent.pathLookAhead(mech, f.roster);
        assertEquals(3.45f, horizon.x());
        assertEquals(3.3f, horizon.y());
        assertEquals(MovementService.MotionResult.ARRIVED, f.move(mech, 1f));
        assertEquals(3.45f, f.world.x(mech), 0.00001f);
        assertEquals(3.3f, f.world.y(mech), 0.00001f);
        assertEquals(Math.hypot(0.25f, 0.1f), f.gait(mech), 0.00001f);
    }

    @Test
    void bulwarkFollowsTwoCellCenterlineAndRetainsACompletedEndpointWitness() {
        Fixture f = new Fixture(false);
        f.floor(1, 3, 11, 5);
        long mech = f.mech(MechVariant.BULWARK, 2f, 4f);
        var planned = new ClearanceRoutePlanner().findRoute(f.grid, 2f, 4f,
                9.5f, 3.5f, f.roster.radius(mech), 0.5f, 4000);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, planned.status());
        f.install(mech, 9, 3, planned.waypoints().toArray(ClearanceRoutePlanner.Point[]::new));
        f.face(mech, MechLocomotion.continuousFacing(1f, 0f));
        assertTrue(f.movement.pathTargetsCell(mech, 9, 3));
        assertFalse(f.movement.pathTargetsCell(mech, 9, 4));
        for (int tick = 0; tick < 300 && !f.movement.continuousRoute(mech).completed(); tick++) {
            assertNotEquals(MovementService.MotionResult.BLOCKED, f.move(mech, 0.1f));
            assertEquals(4f, f.world.y(mech));
            assertTrue(ManualTerrainMotion.canStand(f.grid, f.world.x(mech), f.world.y(mech), 0.6f));
        }
        assertTrue(f.movement.continuousRoute(mech).completed());
        assertTrue(f.movement.atCell(mech, 9, 3));
        assertEquals(9.5f, f.world.x(mech), 0.00001f);
        f.movement.setPathRef(mech, new int[0]);
        f.movement.setPathIdx(mech, 0);
        assertEquals(0, f.movement.waypointCount(mech));
        assertTrue(f.movement.atCell(mech, 9, 3));
        f.world.setPos(mech, 9.5f, 4.4f);
        assertFalse(f.movement.atCell(mech, 9, 3), "a displaced body cannot reuse an old arrival witness");
    }

    @Test
    void lTurnConsumesOnlyLegalSegmentsAndWaitsForTheNextHipBearing() {
        Fixture f = new Fixture(false);
        f.floor(1, 1, 8, 3);
        f.floor(6, 1, 8, 11);
        long mech = f.mech(MechVariant.BULWARK, 2f, 2f);
        var planned = new ClearanceRoutePlanner().findRoute(f.grid, 2f, 2f, 7f, 9f, 0.6f, 0f, 4000);
        assertEquals(ClearanceRoutePlanner.Status.FOUND, planned.status());
        f.install(mech, 7, 9, planned.waypoints().toArray(ClearanceRoutePlanner.Point[]::new));
        boolean turned = false;
        for (int tick = 0; tick < 1800 && !f.movement.continuousRoute(mech).completed(); tick++) {
            float x = f.world.x(mech), y = f.world.y(mech);
            var outcome = f.move(mech, DT);
            turned |= outcome == MovementService.MotionResult.HELD_FOR_TURN;
            assertNotEquals(MovementService.MotionResult.BLOCKED, outcome);
            assertTrue(Math.hypot(f.world.x(mech) - x, f.world.y(mech) - y)
                    <= f.movement.moveSpeed(mech) * DT + 0.00001f);
            assertTrue(ManualTerrainMotion.canStand(f.grid, f.world.x(mech), f.world.y(mech), 0.6f));
            f.hips.tick(DT);
        }
        assertTrue(turned);
        assertTrue(f.movement.continuousRoute(mech).completed());
        assertEquals(7f, f.world.x(mech), 0.00001f);
        assertEquals(9f, f.world.y(mech), 0.00001f);
    }

    @Test
    void newClosedEdgeStopsTheWholeBodyWithoutCompletingTheRouteOrInventingGait() {
        Fixture f = new Fixture(true);
        long mech = f.mech(MechVariant.BULWARK, 2f, 3f);
        f.install(mech, 8, 3, new ClearanceRoutePlanner.Point(2f, 3f), new ClearanceRoutePlanner.Point(8f, 3f));
        f.face(mech, MechLocomotion.continuousFacing(1f, 0f));
        for (int y = 0; y < 12; y++) f.grid.blockSharedEdge(3, y, Direction.E);
        assertEquals(MovementService.MotionResult.BLOCKED, f.move(mech, 10f));
        assertTrue(f.world.x(mech) <= 4f - 0.6f);
        assertEquals((f.world.x(mech) - 2f) / 10f, f.movement.velX(mech));
        assertFalse(f.movement.continuousRoute(mech).completed());
        assertFalse(f.movement.atCell(mech, 8, 3));
        float gait = f.gait(mech);
        assertEquals(MovementService.MotionResult.BLOCKED, f.move(mech, DT));
        assertEquals(0f, f.movement.velX(mech));
        assertEquals(gait, f.gait(mech));
    }

    @Test
    void everyMechSeparationCorrectionUsesItsActualRadius() {
        for (MechVariant variant : MechVariant.values()) {
            Fixture f = new Fixture(true);
            float x = 4f - variant.radius;
            long mech = f.mech(variant, x, 3f);
            f.mech(variant, x - 0.4f, 3f);
            for (int y = 0; y < 12; y++) f.grid.blockSharedEdge(3, y, Direction.E);
            new SeparationSystem(f.roster, f.index, f.grid).tick(DT);
            assertEquals(x, f.world.x(mech), 0.00001f);
            assertTrue(ManualTerrainMotion.canStand(f.grid, f.world.x(mech), f.world.y(mech), variant.radius));
        }
    }

    @Test
    void collisionStallTracksExactEndpointChangesWithinOneProjectedCell() {
        Fixture f = new Fixture(true);
        long mech = f.mech(MechVariant.BULWARK, 3f, 3f);
        f.install(mech, 5, 3, new ClearanceRoutePlanner.Point(3f, 3f), new ClearanceRoutePlanner.Point(5f, 3f));
        var escape = new MechCollisionEscapeSystem(f.roster.entityWorld(), f.roster.components());
        var loadout = f.world.mechLoadout(mech);
        escape.tick(DT);
        assertEquals(2f, loadout.collisionBestRemainingDistance);
        escape.tick(2f);
        assertTrue(loadout.collisionEscapeActive);
        f.install(mech, 5, 3, new ClearanceRoutePlanner.Point(3f, 3f), new ClearanceRoutePlanner.Point(5.4f, 3f));
        escape.tick(DT);
        assertFalse(loadout.collisionEscapeActive);
        assertEquals(0f, loadout.collisionStallSeconds);
        assertEquals(2.4f, loadout.collisionBestRemainingDistance, 0.00001f);
    }

    @Test
    void legacyMoverCannotConsumeContinuousRouteWithoutTerrain() {
        Fixture f = new Fixture(true);
        long mech = f.mech(MechVariant.BULWARK, 3f, 3f);
        f.install(mech, 5, 3, new ClearanceRoutePlanner.Point(3f, 3f), new ClearanceRoutePlanner.Point(5f, 3f));
        assertThrows(IllegalStateException.class, () -> f.movement.advanceAlongPath(f.world, mech, DT));
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(12, 12);
        final UnitSpatialIndex index = new UnitSpatialIndex(12, 12);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final MovementService movement = roster.movement();
        final World world = roster.world();
        final MechLocomotionSystem hips = new MechLocomotionSystem(roster.entityWorld(), roster.components(), roster);

        Fixture(boolean open) { if (open) floor(0, 0, 12, 12); }

        void floor(int x0, int y0, int x1, int y1) {
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) grid.setWalkableFloor(x, y);
        }

        long mech(MechVariant variant, float x, float y) {
            long id = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH,
                    (int) x, (int) y).mechVariant(variant));
            world.setPos(id, x, y);
            world.attachMechLoadout(id, variant.createLoadout(variant.defaultRole));
            return id;
        }

        void install(long id, int requestedX, int requestedY, ClearanceRoutePlanner.Point... points) {
            ContinuousRoute route = new ContinuousRoute(requestedX, requestedY, roster.radius(id),
                    grid.topologyRevision(), List.of(points));
            movement.setPathRef(id, route.projection());
            movement.setPathIdx(id, points.length > 1 ? 1 : 0);
            movement.setContinuousRouteRef(id, route);
        }

        MovementService.MotionResult move(long id, float dt) {
            movement.beginTick(dt);
            return movement.advanceAlongPath(world, id, dt, grid, roster.radius(id));
        }

        void face(long id, float angle) {
            roster.entityWorld().setFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, angle);
        }

        float facing(long id) {
            return roster.entityWorld().getFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        }

        float gait(long id) {
            return roster.entityWorld().getFloat(id, roster.components().MOVEMENT,
                    BattleComponents.MOVEMENT_GAIT_PHASE);
        }
    }
}
