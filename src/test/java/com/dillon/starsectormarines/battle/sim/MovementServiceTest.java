package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MovementServiceTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    /**
     * Regression for the born-exhausted one-cell path: findPath(start == goal)
     * yields {@code {x, y}}, and a unit standing inside that cell but outside
     * the arrival band must still be pulled onto the center — behaviors that
     * repath every window replace the in-flight path with exactly this shape
     * during the final approach.
     */
    @Test
    public void oneCellPathPullsAnOffCenterUnitOntoTheCenter() {
        BattleSimulation sim = arena();
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 5, 5));
        // Inside cell (5,5) but outside ARRIVE_RADIUS of its center.
        sim.world().setPos(marine, 5.95f, 5.5f);
        assertFalse(sim.movement().atCell(marine, 5, 5));

        sim.setPath(marine, new int[]{5, 5});
        assertEquals(0, sim.world().pathIdx(marine),
                "a one-cell path starts un-exhausted so the mover pulls to the pin");
        assertFalse(sim.movement().settled(marine));

        for (int i = 0; i < 30 && !sim.movement().settled(marine); i++) {
            sim.advanceMovement(marine);
        }
        assertTrue(sim.movement().settled(marine));
        assertTrue(sim.movement().atCell(marine, 5, 5));
        assertEquals(5.5f, sim.world().x(marine), 1e-4f, "pinned on the cell center");
        assertEquals(5.5f, sim.world().y(marine), 1e-4f);
    }

    /** Applied velocity is per-tick truth: written on translation, zeroed by the tick prologue. */
    @Test
    public void velocityReflectsOnlyMovementAppliedThisTick() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 5, 5));
        sim.setPath(marine, new int[]{5, 5, 8, 5});

        sim.advanceMovement(marine);
        float vx = sim.getEntityWorld().getFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_X);
        assertEquals(UnitType.MARINE.moveSpeed, vx, 1e-3f,
                "translating east at full speed authors +x velocity");

        // A behavior that holds this tick never calls advanceAlongPath; the
        // prologue's clear is what keeps the unit reading as not moving.
        sim.movement().beginTick(BattleSimulation.TICK_DT);
        assertEquals(0f, sim.getEntityWorld().getFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_X), 0f);
        assertEquals(0f, sim.getEntityWorld().getFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y), 0f);
    }
}
