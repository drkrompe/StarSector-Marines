package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualInfantryMovementTest {
    private static final float DT = 1f / 30f;

    @Test
    void diagonalUsesCurrentSpeedAndPublishesAppliedVelocityAndGait() {
        Fixture f = new Fixture();
        long marine = f.marine(5, 5);
        f.roster.entityWorld().setFloat(marine, f.roster.components().MOVEMENT,
                BattleComponents.MOVEMENT_MOVE_SPEED, 3f);

        f.movement.moveDirect(marine, f.grid, 1f, 1f, UnitType.MARINE.radius, 0.25f);

        assertEquals(0.75f, Math.hypot(f.world.x(marine) - 5.5f,
                f.world.y(marine) - 5.5f), 0.00001f);
        assertEquals(3f, Math.hypot(f.movement.velX(marine), f.movement.velY(marine)), 0.00001f);
        assertEquals(0.75f, f.gait(marine), 0.00001f);
        f.movement.moveDirect(marine, f.grid, 0f, 0f, UnitType.MARINE.radius, DT);
        assertEquals(0f, f.movement.velX(marine));
        assertEquals(0f, f.movement.velY(marine));
        assertEquals(0.75f, f.gait(marine), 0.00001f);
    }

    @Test
    void pushingAgainstClosedEdgeHasNoVelocityOrGaitUntilItOpens() {
        Fixture f = new Fixture();
        long marine = f.marine(3, 2);
        f.world.setPos(marine, 4f - UnitType.MARINE.radius, 2.5f);
        f.grid.blockSharedEdge(3, 2, Direction.E);

        f.movement.moveDirect(marine, f.grid, 1f, 0f, UnitType.MARINE.radius, DT);

        assertEquals(0f, f.movement.velX(marine), 0.00001f);
        assertEquals(0f, f.gait(marine), 0.00001f);
        f.grid.openSharedEdge(3, 2, Direction.E);
        f.movement.moveDirect(marine, f.grid, 1f, 0f, UnitType.MARINE.radius, DT);
        assertEquals(UnitType.MARINE.moveSpeed, f.movement.velX(marine), 0.00001f);
        assertTrue(f.gait(marine) > 0f);
    }

    @Test
    void separationCannotPushTheControlledEnvelopeThroughAClosedEdge() {
        Fixture f = new Fixture();
        long controlled = f.marine(3, 2);
        long neighbor = f.marine(3, 2);
        f.world.setPos(controlled, 3.65f, 2.5f);
        f.world.setPos(neighbor, 3.4f, 2.5f);
        f.grid.blockSharedEdge(3, 2, Direction.E);
        SeparationSystem separation = new SeparationSystem(f.roster, f.index, f.grid);

        for (int tick = 0; tick < 8; tick++) {
            f.movement.beginTick(DT);
            float previousX = f.world.x(controlled);
            separation.tick(DT, controlled);
            assertTrue(f.world.x(controlled) <= 4f - UnitType.MARINE.radius);
            assertEquals((f.world.x(controlled) - previousX) / DT,
                    f.movement.velX(controlled), 0.00001f);
        }
        assertTrue(f.world.x(controlled) > 3.65f, "physical separation still applies where legal");
        assertTrue(f.gait(controlled) > 0f, "the legal shove advances the controlled body's gait");
    }

    @Test
    void controlledMemberDoesNotReceiveOrAnchorFormationSteering() {
        Fixture f = new Fixture();
        long controlled = f.marine(5, 5);
        long other = f.marine(7, 5);
        int squad = f.roster.mintSquad(Faction.MARINE, controlled);
        f.roster.squad().assignSquad(controlled, squad);
        f.roster.squad().assignSquad(other, squad);
        f.movement.setPathRef(controlled, new int[]{5, 5, 5, 9});
        f.movement.setPathRef(other, new int[]{7, 5, 7, 9});
        f.movement.setPathIdx(controlled, 0);
        f.movement.setPathIdx(other, 0);

        new SeparationSystem(f.roster, f.index, f.grid).tick(DT, controlled);

        assertEquals(5.5f, f.world.x(controlled));
        assertEquals(5.5f, f.world.y(controlled));
        assertEquals(7.5f, f.world.x(other));
        assertEquals(5.5f, f.world.y(other));
    }

    @Test
    void swarmSteeringLeavesTheControlledUnitToItsOwnDrive() {
        Fixture f = new Fixture();
        long controlled = f.marine(4, 5);
        long other = f.marine(4, 7);
        f.roster.spawn(new EntitySpec("alien", Faction.DEFENDER, UnitType.ALIEN, 6, 6));

        new SwarmAvoidanceSystem(f.roster, f.index, f.grid).tick(DT, controlled);

        assertEquals(4.5f, f.world.x(controlled));
        assertEquals(5.5f, f.world.y(controlled));
        assertEquals(0f, f.movement.velX(controlled));
        assertTrue(f.world.x(other) < 4.5f, "ordinary infantry still responds to the swarm");
    }

    @Test
    void entryClearanceUsesTheSameEnvelopeAsManualMotion() {
        Fixture f = new Fixture();
        f.grid.blockSharedEdge(3, 2, Direction.E);
        assertFalse(ManualTerrainMotion.canStand(f.grid, 3.9f, 2.5f, UnitType.MARINE.radius));
        assertTrue(ManualTerrainMotion.canStand(f.grid, 3.5f, 2.5f, UnitType.MARINE.radius));
        f.grid.openSharedEdge(3, 2, Direction.E);
        assertTrue(ManualTerrainMotion.canStand(f.grid, 3.9f, 2.5f, UnitType.MARINE.radius));
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(12, 12);
        final UnitSpatialIndex index = new UnitSpatialIndex(12, 12);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final MovementService movement = roster.movement();
        final World world = roster.world();

        Fixture() {
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
            }
        }

        long marine(int x, int y) {
            return roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, x, y));
        }

        float gait(long id) {
            return roster.entityWorld().getFloat(id, roster.components().MOVEMENT,
                    BattleComponents.MOVEMENT_GAIT_PHASE);
        }
    }
}
