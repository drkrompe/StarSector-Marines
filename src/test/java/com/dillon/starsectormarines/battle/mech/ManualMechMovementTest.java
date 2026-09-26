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
    void pivotsBeforeDrivingTheExactSubcellBearing() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.BULWARK, 5f, 5f);
        f.face(mech, 180f);
        float desired = MechLocomotion.continuousFacing(.25f, .1f);
        f.drive(mech, .25f, .1f, DT);
        assertEquals(5f, f.world.x(mech));
        assertEquals(0f, f.movement.velX(mech));
        assertEquals(0f, f.gait(mech));
        for (int tick = 0; tick < 180 && f.world.x(mech) == 5f; tick++) {
            f.drive(mech, .25f, .1f, DT);
            if (Math.abs(MechLocomotion.deltaDegrees(f.facing(mech), desired))
                    > MechLocomotion.MOVE_ALIGNMENT_DEGREES) {
                assertEquals(5f, f.world.x(mech));
                assertEquals(0f, f.gait(mech));
            }
        }
        assertTrue(f.world.x(mech) > 5f);
        assertEquals(.4f, f.movement.velY(mech) / f.movement.velX(mech), .0001f);
        assertEquals(f.movement.moveSpeed(mech) * Math.hypot(.25f, .1f),
                Math.hypot(f.movement.velX(mech), f.movement.velY(mech)), .0001f);
    }

    @Test
    void changedDirectionBrakesOpposingHipMomentumAndStopsTranslation() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.HOUND, 5f, 5f);
        f.face(mech, MechLocomotion.continuousFacing(1f, 0f));
        f.drive(mech, 1f, 0f, DT);
        assertTrue(f.movement.velX(mech) > 0f);
        float x = f.world.x(mech), gait = f.gait(mech), facing = f.facing(mech);
        f.angularVelocity(mech, 90f);
        f.drive(mech, 0f, -1f, DT);
        assertEquals(78f, f.angularVelocity(mech), .0001f,
                "reversing intent first brakes angular momentum instead of snapping it");
        assertTrue(f.facing(mech) > facing);
        assertEquals(x, f.world.x(mech));
        assertEquals(5f, f.world.y(mech));
        assertEquals(0f, f.movement.velX(mech));
        assertEquals(0f, f.movement.velY(mech));
        assertEquals(gait, f.gait(mech));
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
    void diagonalIsNormalizedAtCurrentSpeedAndZeroTimePreservesHipMomentum() {
        Fixture f = new Fixture();
        long mech = f.mech(MechVariant.SIROCCO, 5f, 5f);
        f.face(mech, MechLocomotion.continuousFacing(1f, 1f));
        f.roster.entityWorld().setFloat(mech, f.roster.components().MOVEMENT,
                BattleComponents.MOVEMENT_MOVE_SPEED, 3f);
        f.drive(mech, 1f, 1f, .25f);
        assertEquals(.75f, Math.hypot(f.world.x(mech) - 5f, f.world.y(mech) - 5f), .00001f);
        assertEquals(3f, Math.hypot(f.movement.velX(mech), f.movement.velY(mech)), .00001f);
        assertEquals(.75f, f.gait(mech), .00001f);
        f.angularVelocity(mech, 90f);
        float facing = f.facing(mech);
        f.drive(mech, -1f, 0f, 0f);
        assertEquals(facing, f.facing(mech));
        assertEquals(90f, f.angularVelocity(mech));
        assertEquals(0f, f.movement.velX(mech));
        assertEquals(.75f, f.gait(mech), .00001f);
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
            assertEquals(x + f.movement.moveSpeed(mech) * DT, f.world.x(mech), .00001f);
            f.drive(mech, 1f, 0f, 100f);
            assertTrue(f.world.x(mech) <= 12f - variant.radius);
            f.face(mech, MechLocomotion.continuousFacing(-1f, 0f));
            f.drive(mech, -1f, 0f, 100f);
            assertTrue(f.world.x(mech) >= variant.radius);
            assertTrue(ManualTerrainMotion.canStand(f.grid, f.world.x(mech), f.world.y(mech), variant.radius));
        }
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(12, 12);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(12, 12), null);
        final MovementService movement = roster.movement();
        final World world = roster.world();
        final MechLocomotionSystem hips = new MechLocomotionSystem(roster.entityWorld(), roster.components(), roster);

        Fixture() {
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }

        long mech(MechVariant variant, float x, float y) {
            long id = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH,
                    (int) x, (int) y).mechVariant(variant));
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
