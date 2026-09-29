package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualMechMovementTest {
    private static final float DT = 1f / 30f;

    @Test
    void acceleratesDespiteHipMisalignmentAndRetainsMomentumAcrossTickReset() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.BULWARK, 5f, 5f);
        f.face(mech, 180f);
        f.drive(mech, 1f, .4f, DT);
        assertTrue(f.world.x(mech) > 5f);
        assertTrue(f.movement.velX(mech) < f.movement.moveSpeed(mech));
        assertEquals(.4f, f.movement.velY(mech) / f.movement.velX(mech), .002f);
        float velocity = f.movement.velX(mech);
        f.movement.beginTick(DT);
        f.drive(mech, 1f, .4f, DT);
        assertTrue(f.movement.velX(mech) > velocity);
    }

    @Test
    void changedDirectionRetainsTranslationWhileHipsBrakeTheirOldSwing() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.HOUND, 5f, 5f);
        f.face(mech, MechLocomotion.continuousFacing(1f, 0f));
        for (int tick = 0; tick < 15; tick++) f.drive(mech, 1f, 0f, DT);
        float x = f.world.x(mech), facing = f.facing(mech);
        f.angularVelocity(mech, 90f);
        f.drive(mech, 0f, -1f, DT);
        assertEquals(78f, f.angularVelocity(mech), .0001f);
        assertTrue(f.facing(mech) > facing);
        assertTrue(f.world.x(mech) > x);
        assertTrue(f.world.y(mech) < 5f);
        assertTrue(f.movement.velX(mech) > 0f);
        assertTrue(f.movement.velY(mech) < 0f);
    }

    @Test
    void neutralDriveBrakesOnceAndTheAiHipPassCannotTurnTowardItsTarget() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.BULWARK, 5f, 5f);
        long other = f.mech(MechVariant.HOUND, 7f, 5f);
        long target = f.roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.ALIEN, 1, 5));
        f.roster.combat().setTargetId(mech, target);
        f.roster.combat().setTargetId(other, target);
        f.face(mech, 0f);
        f.face(other, 0f);
        f.angularVelocity(mech, 90f);
        f.drive(mech, 0f, 0f, DT);
        float facing = f.facing(mech);
        assertEquals(78f, f.angularVelocity(mech), .0001f);
        f.hips.tick(DT, mech);
        assertEquals(facing, f.facing(mech));
        assertEquals(78f, f.angularVelocity(mech), .0001f);
        assertTrue(Math.abs(f.angularVelocity(other)) > 0f, "the ordinary actor still turns");
        for (int tick = 0; tick < 30; tick++) {
            f.drive(mech, 0f, 0f, DT);
            f.hips.tick(DT, mech);
        }
        assertEquals(0f, f.angularVelocity(mech));
        assertEquals(5f, f.world.x(mech));
        assertEquals(5f, f.world.y(mech));
        assertEquals(0f, f.gait(mech));
    }

    @Test
    void diagonalRespectsLiveSpeedAndZeroTimePreservesAllMotionState() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.SIROCCO, 5f, 5f);
        f.roster.entityWorld().setFloat(mech, f.roster.components().MOVEMENT,
                BattleComponents.MOVEMENT_MOVE_SPEED, 3f);
        f.drive(mech, 1f, 1f, .25f);
        assertTrue(Math.hypot(f.movement.velX(mech), f.movement.velY(mech)) < 3f);
        assertTrue(f.gait(mech) > 0f);
        f.angularVelocity(mech, 90f);
        float facing = f.facing(mech), x = f.world.x(mech), gait = f.gait(mech);
        float velocity = f.world.mechLoadout(mech).manualDrive.velocityX();
        f.drive(mech, -1f, 0f, 0f);
        assertEquals(facing, f.facing(mech));
        assertEquals(90f, f.angularVelocity(mech));
        assertEquals(x, f.world.x(mech));
        assertEquals(gait, f.gait(mech));
        assertEquals(velocity, f.world.mechLoadout(mech).manualDrive.velocityX());
    }

    @Test
    void keyReleaseCoastsAndThenComesToRest() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.BULWARK, 5f, 5f);
        for (int tick = 0; tick < 15; tick++) f.drive(mech, 1f, 0f, DT);
        float x = f.world.x(mech), velocity = f.movement.velX(mech);
        f.drive(mech, 0f, 0f, DT);
        assertTrue(f.world.x(mech) > x);
        assertTrue(f.movement.velX(mech) > 0f);
        assertTrue(f.movement.velX(mech) < velocity);
        for (int tick = 0; tick < 120; tick++) f.drive(mech, 0f, 0f, DT);
        assertEquals(0f, f.world.mechLoadout(mech).manualDrive.velocityX());
    }

    @Test
    void fullRadiusStopsAtCurrentClosedEdgesAndMapBoundsWithoutBankedMotion() {
        for (MechVariant variant : MechVariant.values()) {
            Fixture f = new Fixture();
            long mech = f.mech(variant, 2f, 3f);
            f.face(mech, MechLocomotion.continuousFacing(1f, 0f));
            for (int y = 0; y < 12; y++) f.grid.blockSharedEdge(3, y, Direction.E);
            f.drive(mech, 1f, 0f, 10f);
            assertTrue(f.world.x(mech) <= 4f - variant.radius);
            assertTrue(ManualTerrainMotion.canStand(f.grid, f.world.x(mech), f.world.y(mech), variant.radius));
            assertEquals((f.world.x(mech) - 2f) / 10f, f.movement.velX(mech));
            float x = f.world.x(mech), gait = f.gait(mech);
            f.drive(mech, 1f, 0f, DT);
            assertEquals(0f, f.movement.velX(mech));
            assertEquals(gait, f.gait(mech));
            for (int y = 0; y < 12; y++) f.grid.openSharedEdge(3, y, Direction.E);
            f.drive(mech, 1f, 0f, DT);
            assertTrue(f.world.x(mech) > x);
            assertTrue(f.world.x(mech) < x + f.movement.moveSpeed(mech) * DT);
            f.drive(mech, 1f, 0f, 1000f);
            assertTrue(f.world.x(mech) <= 512f - variant.radius);
            f.face(mech, MechLocomotion.continuousFacing(-1f, 0f));
            f.drive(mech, -1f, 0f, 1000f);
            assertTrue(f.world.x(mech) >= variant.radius);
            assertTrue(ManualTerrainMotion.canStand(f.grid, f.world.x(mech), f.world.y(mech), variant.radius));
        }
    }

    @Test
    void firstWallContactDropsNormalMomentumAndKeepsTheLegalSlide() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.HOUND, 3.4f, 3f);
        for (int y = 0; y < 12; y++) f.grid.blockSharedEdge(3, y, Direction.E);
        f.drive(mech, 1f, 1f, .5f);
        var drive = f.world.mechLoadout(mech).manualDrive;
        assertEquals(0f, drive.velocityX());
        assertTrue(drive.velocityY() > 0f);
        assertTrue(f.world.y(mech) > 3f);
        for (int y = 0; y < 12; y++) f.grid.openSharedEdge(3, y, Direction.E);
        float x = f.world.x(mech);
        f.drive(mech, 0f, 0f, DT);
        assertEquals(x, f.world.x(mech), "opening cannot release old normal momentum");
    }

    @Test
    void openTravelAtLargeCoordinatesDoesNotMistakeFloatRoundingForCollision() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.BULWARK, 400f, 5f);
        for (int tick = 0; tick < 120; tick++) f.drive(mech, 1f, 0f, DT);
        assertTrue(f.world.mechLoadout(mech).manualDrive.velocityX()
                > f.movement.moveSpeed(mech) * .99f);
        assertTrue(f.movement.velX(mech) > f.movement.moveSpeed(mech) * .99f);
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(512, 12);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(512, 12), null);
        final MovementService movement = roster.movement();
        final World world = roster.world();
        final MechLocomotionSystem hips = new MechLocomotionSystem(roster.entityWorld(), roster.components(), roster);

        Fixture() {
            for (int y = 0; y < 12; y++) for (int x = 0; x < 512; x++) grid.setWalkableFloor(x, y);
        }

        long mech(MechVariant variant, float x, float y) {
            long id = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH,
                    (int) x, (int) y).mechVariant(variant));
            world.attachMechLoadout(id, variant.createLoadout(MechRole.BALANCED));
            world.setPos(id, x, y);
            return id;
        }

        void drive(long id, float x, float y, float dt) {
            movement.moveDirectMech(id, grid, x, y, roster.radius(id), dt);
        }

        void face(long id, float value) {
            roster.entityWorld().setFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, value);
        }

        float facing(long id) {
            return roster.entityWorld().getFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        }

        void angularVelocity(long id, float value) {
            roster.entityWorld().setFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY, value);
        }

        float angularVelocity(long id) {
            return roster.entityWorld().getFloat(id, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY);
        }

        float gait(long id) {
            return roster.entityWorld().getFloat(id, roster.components().MOVEMENT,
                    BattleComponents.MOVEMENT_GAIT_PHASE);
        }
    }
}
