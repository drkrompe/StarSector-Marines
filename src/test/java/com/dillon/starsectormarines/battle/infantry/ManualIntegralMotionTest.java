package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualIntegralMotionTest {
    private static final float DT = 1f / 30f;

    @Test
    void legalDriveCountsAsCrossingBeforeAppliedVelocityExists() {
        Fixture f = new Fixture();
        assertTrue(f.movement.settled(f.marine), "direct control owns no AI path");
        IntegralSystemSystem.PolicyMotion motion = f.motion(1f, 1f);

        assertTrue(motion.underway());
        assertEquals(UnitType.MARINE.moveSpeed, Math.hypot(motion.vx(), motion.vy()), 0.00001f);
        assertEquals(3.5f, f.roster.world().x(f.marine), "preview must not move the wearer");
        assertEquals(0f, f.movement.velX(f.marine), "policy must not publish hypothetical velocity");
        assertEquals(0f, f.roster.entityWorld().getFloat(f.marine, f.roster.components().MOVEMENT,
                BattleComponents.MOVEMENT_GAIT_PHASE));
        assertFalse(f.motion(0f, 0f).underway());
    }

    @Test
    void blockedDriveIsStationaryButLegalWallSlideIsUnderway() {
        Fixture f = new Fixture();
        f.grid.blockSharedEdge(3, 3, Direction.E);
        f.roster.world().setPos(f.marine, 4f - UnitType.MARINE.radius, 3.5f);

        IntegralSystemSystem.PolicyMotion blocked = f.motion(1f, 0f);
        assertFalse(blocked.underway());
        assertEquals(0f, blocked.vx(), 0.00001f);
        assertEquals(0f, blocked.vy());

        IntegralSystemSystem.PolicyMotion sliding = f.motion(1f, 1f);
        assertTrue(sliding.underway());
        assertEquals(0f, sliding.vx(), 0.00001f);
        assertTrue(sliding.vy() > 0f);
        f.grid.openSharedEdge(3, 3, Direction.E);
        assertTrue(f.motion(1f, 0f).underway(), "policy reads current terrain topology");
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(8, 8);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(8, 8), null);
        final MovementService movement = roster.movement();
        final long marine;

        Fixture() {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
            }
            marine = roster.spawn(new EntitySpec("wearer", Faction.MARINE, UnitType.MARINE, 3, 3));
            movement.beginTick(DT);
        }

        IntegralSystemSystem.PolicyMotion motion(float x, float y) {
            ManualTerrainMotion.Result step = movement.previewDirect(marine, grid,
                    x, y, UnitType.MARINE.radius, DT);
            return IntegralSystemSystem.policyMotion(marine, movement, step, DT);
        }
    }
}
