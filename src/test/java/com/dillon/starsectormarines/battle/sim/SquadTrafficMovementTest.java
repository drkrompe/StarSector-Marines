package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadTrafficMovementTest {
    private static final float DT = 1f / 30f;

    @Test
    void sustainedOffsetSpreadsWithoutReplacingThePathOrRepathStamp() {
        Fixture f = new Fixture();
        int[] path = f.eastPath();
        f.install(path);
        f.movement.markRepath(f.member);
        float stamp = f.repathStamp();
        for (int i = 0; i < 90; i++) f.move(0f, 2f, 1f);

        assertTrue(f.world.y(f.member) > 6.8f, "the centerline follower must not cancel the lateral preference");
        assertTrue(f.world.x(f.member) > 7f, "spreading still makes forward progress");
        assertSame(path, f.movement.path(f.member));
        assertEquals(stamp, f.repathStamp());
        assertFalse(f.movement.settled(f.member));
    }

    @Test
    void sustainedOffsetStillArrivesAtTheOriginalFinalCenter() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        for (int i = 0; i < 600 && !f.movement.settled(f.member); i++) f.move(0f, 2f, 1f);
        assertTrue(f.movement.settled(f.member));
        assertEquals(30.5f, f.world.x(f.member));
        assertEquals(5.5f, f.world.y(f.member));
        assertTrue(f.movement.atCell(f.member, 30, 5));
    }

    @Test
    void speedScaleUsesOneBudgetAndVelocityAndGaitMeasureTheAppliedStep() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.move(0f, 2f, 0.4f);
        float traveled = (float) Math.hypot(f.world.x(f.member) - 5.5f, f.world.y(f.member) - 5.5f);
        assertEquals(2f * DT * 0.4f, traveled, 1e-5f);
        assertEquals(traveled / DT, Math.hypot(f.movement.velX(f.member), f.movement.velY(f.member)), 1e-5f);
        assertEquals(traveled, f.entities.getFloat(f.member, f.components.MOVEMENT,
                BattleComponents.MOVEMENT_GAIT_PHASE), 1e-5f);
        float x = f.world.x(f.member), y = f.world.y(f.member);
        f.move(0f, 2f, 4f);
        assertEquals(2f * DT, Math.hypot(f.world.x(f.member) - x, f.world.y(f.member) - y), 1e-5f);
    }

    @Test
    void yieldingDoesNotSettleOrConsumePathProgress() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.move(0f, 2f, 0f);
        assertEquals(5.5f, f.world.x(f.member));
        assertEquals(5.5f, f.world.y(f.member));
        assertEquals(0, f.movement.pathIdx(f.member));
        assertFalse(f.movement.settled(f.member));
        assertEquals(0f, f.movement.velX(f.member));
        assertEquals(0f, f.movement.velY(f.member));
    }

    @Test
    void closedSideEdgeRejectsSpreadingButPreservesOrdinaryForwardMotion() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.world.setPos(f.member, 5.5f, 5.9f);
        f.grid.blockSharedEdge(5, 5, Direction.N);
        f.move(0f, 2f, 1f);
        assertTrue(f.world.x(f.member) > 5.5f, "unsafe spreading falls back to the legal original corridor");
        assertTrue(f.world.y(f.member) < 5.9f);
        assertTrue(f.world.y(f.member) < 6f);
    }

    @Test
    void unsafeReturnFromAnOffsetHoldsInsteadOfCuttingThroughTheWall() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.world.setPos(f.member, 5.5f, 6.1f);
        f.grid.blockSharedEdge(5, 5, Direction.N);
        f.move(0f, -2f, 1f);
        assertEquals(5.5f, f.world.x(f.member));
        assertEquals(6.1f, f.world.y(f.member));
        assertEquals(0, f.movement.pathIdx(f.member));
        assertEquals(0f, f.movement.velX(f.member));
        assertEquals(0f, f.movement.velY(f.member));
    }

    @Test
    void closedDiagonalCannotBeCutByTheTrafficFollower() {
        Fixture f = new Fixture();
        f.install(new int[]{5, 5, 6, 6, 7, 7});
        f.world.setPos(f.member, 5.9f, 5.9f);
        f.grid.blockEdge(5, 5, Direction.NE);
        f.move(0f, 0f, 1f);
        assertEquals(5.9f, f.world.x(f.member));
        assertEquals(5.9f, f.world.y(f.member));
    }

    @Test
    void zeroOffsetMatchesTheOrdinaryFollowerOnOpenGround() {
        Fixture traffic = new Fixture(), ordinary = new Fixture();
        traffic.install(traffic.eastPath());
        ordinary.install(ordinary.eastPath());
        for (int i = 0; i < 60; i++) {
            traffic.move(0f, 0f, 1f);
            ordinary.movement.advanceAlongPath(ordinary.world, ordinary.member, DT);
            assertEquals(ordinary.world.x(ordinary.member), traffic.world.x(traffic.member), 1e-5f);
            assertEquals(ordinary.world.y(ordinary.member), traffic.world.y(traffic.member), 1e-5f);
            assertEquals(ordinary.movement.pathIdx(ordinary.member), traffic.movement.pathIdx(traffic.member));
        }
    }

    @Test
    void segmentProbeChecksIntermediateTerrainAndReciprocalEdgesWithinAHardBound() {
        Fixture f = new Fixture();
        assertTrue(MovementService.trafficSegmentClear(f.grid, 5.5f, 5.5f, 20.5f, 5.5f));
        f.grid.blockEdge(12, 5, Direction.W);
        assertFalse(MovementService.trafficSegmentClear(f.grid, 5.5f, 5.5f, 20.5f, 5.5f));
        f.grid.openEdge(12, 5, Direction.W);
        f.grid.setWalkable(12, 5, false);
        assertFalse(MovementService.trafficSegmentClear(f.grid, 5.5f, 5.5f, 20.5f, 5.5f));
        assertFalse(MovementService.trafficSegmentClear(f.grid, 1.5f, 4.5f, 39.5f, 4.5f));
        assertFalse(MovementService.trafficSegmentClear(f.grid, Float.NaN, 4.5f, 5.5f, 4.5f));
    }

    @Test
    void oneCellPathIgnoresTheTrafficOffsetAndRecenters() {
        Fixture f = new Fixture();
        f.install(new int[]{5, 5});
        f.world.setPos(f.member, 5.95f, 5.5f);
        for (int i = 0; i < 30 && !f.movement.settled(f.member); i++) f.move(0f, 3f, 1f);
        assertTrue(f.movement.settled(f.member));
        assertEquals(5.5f, f.world.x(f.member));
        assertEquals(5.5f, f.world.y(f.member));
    }

    @Test
    void ordinaryContactHandbackRetainsTerrainSafetyWithoutTrafficSteeringOrYielding() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.move(0f, 2f, 0.25f);
        assertTrue(f.returnGuard());
        f.world.setPos(f.member, 5.5f, 6.1f);
        f.grid.blockSharedEdge(5, 5, Direction.N);

        // Contact and reflex movement use this ordinary entry point, without
        // consulting the squad traffic adapter or its now-obsolete snapshot.
        f.movement.advanceAlongPath(f.world, f.member, DT);
        assertEquals(5.5f, f.world.x(f.member));
        assertEquals(6.1f, f.world.y(f.member));
        assertEquals(0f, f.movement.velY(f.member));
        assertTrue(f.returnGuard());

        f.grid.openSharedEdge(5, 5, Direction.N);
        f.movement.advanceAlongPath(f.world, f.member, DT);
        assertEquals(2f, Math.hypot(f.movement.velX(f.member), f.movement.velY(f.member)), 1e-5f,
                "the old traffic slowdown must not survive handback");
        assertTrue(f.world.y(f.member) < 6.1f, "the old positive offset must not survive handback");
    }

    @Test
    void clearedAndReplacedPathPreservesTheReturnGuard() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.move(0f, 2f, 1f);
        f.install(new int[0]);
        f.movement.advanceAlongPath(f.world, f.member, DT);
        assertTrue(f.returnGuard(), "clearing intent is not proof the body rejoined a legal corridor");
        f.world.setPos(f.member, 5.5f, 6.1f);
        f.grid.blockSharedEdge(5, 5, Direction.N);
        f.install(new int[]{5, 5, 6, 5, 7, 5});

        f.movement.advanceAlongPath(f.world, f.member, DT);
        assertEquals(5.5f, f.world.x(f.member));
        assertEquals(6.1f, f.world.y(f.member));
        assertTrue(f.returnGuard());
    }

    @Test
    void reachingTheReplacementEndpointReleasesTheReturnGuard() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.move(0f, 2f, 1f);
        assertTrue(f.returnGuard());
        f.install(new int[]{5, 5});
        for (int i = 0; i < 30 && !f.movement.settled(f.member); i++) {
            f.movement.advanceAlongPath(f.world, f.member, DT);
        }
        assertTrue(f.movement.settled(f.member));
        assertEquals(5.5f, f.world.x(f.member));
        assertEquals(5.5f, f.world.y(f.member));
        assertFalse(f.returnGuard());
    }

    @Test
    void rejectedOrZeroSpeedOffsetsDoNotArmAFormerParticipantGuard() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.move(0f, 2f, 0f);
        assertFalse(f.returnGuard());
        f.world.setPos(f.member, 5.5f, 5.9f);
        f.grid.blockSharedEdge(5, 5, Direction.N);
        f.move(0f, 2f, 1f);
        assertFalse(f.returnGuard(), "a rejected shifted join applied only ordinary route motion");
        f.move(0f, 0f, 1f);
        assertFalse(f.returnGuard(), "safe zero-offset traffic checks alone do not mark displacement");
    }

    @Test
    void neverParticipantRetainsOrdinaryMovementWithoutRequiringTrafficTerrain() {
        Fixture f = new Fixture();
        f.install(f.eastPath());
        f.movement.setNavigationGrid(null);
        f.movement.advanceAlongPath(f.world, f.member, DT);
        assertEquals(5.5f + 2f * DT, f.world.x(f.member), 1e-5f);
        assertEquals(5.5f, f.world.y(f.member));
        assertFalse(f.returnGuard());
    }

    private static final class Fixture {
        final EntityWorld entities = new EntityWorld();
        final BattleComponents components = new BattleComponents(entities);
        final MovementService movement = new MovementService(entities, components);
        final World world = new World(entities, components, new CombatService(entities, components), movement);
        final NavigationGrid grid = new NavigationGrid(40, 20);
        final long member = entities.createEntity(components.POSITION, components.MOVEMENT);

        Fixture() {
            for (int y = 0; y < 20; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
            movement.setNavigationGrid(grid);
            world.setPos(member, 5.5f, 5.5f);
            entities.setFloat(member, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_SPEED, 2f);
        }

        int[] eastPath() {
            int[] path = new int[52];
            for (int i = 0; i < 26; i++) {
                path[i * 2] = i + 5;
                path[i * 2 + 1] = 5;
            }
            return path;
        }

        void install(int[] path) {
            movement.setPathRef(member, path);
            movement.setPathIdx(member, 0);
        }

        void move(float x, float y, float speed) {
            movement.advanceAlongPath(world, member, DT, x, y, speed);
        }

        float repathStamp() {
            return entities.getFloat(member, components.MOVEMENT, BattleComponents.MOVEMENT_LAST_REPATH_TIME);
        }

        boolean returnGuard() {
            return entities.getInt(member, components.MOVEMENT,
                    BattleComponents.MOVEMENT_TRAFFIC_RETURN_GUARD) != 0;
        }
    }
}
