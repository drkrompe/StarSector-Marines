package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MechLocomotionTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void turnsInPlaceBeforeStartingANewPathSegment() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        sim.setPath(mech, new int[]{5, 5, 6, 5});
        MechLocomotionSystem steering = new MechLocomotionSystem(
                sim.getEntityWorld(), c, sim.getRoster());

        sim.advanceMovement(mech);
        // Misaligned chassis: the mover's pivot gate holds translation, so the
        // carrot-picker hasn't crossed the first waypoint yet.
        assertEquals(1, sim.world().pathIdx(mech), "pivot gate blocks translation while misaligned");
        assertEquals(5f, sim.world().renderX(mech), 0.0001f);

        steering.tick(BattleSimulation.TICK_DT);
        assertEquals(-174f, sim.getEntityWorld().getFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES), 0.001f);

        for (int i = 0; i < 20; i++) {
            sim.advanceMovement(mech);
            steering.tick(BattleSimulation.TICK_DT);
        }
        assertTrue(sim.world().x(mech) > 5.5f, "chassis has translated forward off its spawn-cell center once aligned");
        assertTrue(sim.world().renderX(mech) > 5f);
    }

    @Test
    public void pathBearingOwnsHeadingWhileTravelIsQueued() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long eastTarget = sim.spawn(new EntitySpec("target", Faction.MARINE, UnitType.MARINE, 8, 5));
        sim.setPath(mech, new int[]{5, 5, 5, 6});
        sim.world().setTargetId(mech, eastTarget);

        new MechLocomotionSystem(sim.getEntityWorld(), c, sim.getRoster())
                .tick(BattleSimulation.TICK_DT);

        // North is the queued path bearing; the east-facing target must not snap
        // the chassis away from its travel direction.
        assertEquals(174f, sim.getEntityWorld().getFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES), 0.001f);
    }

    @Test
    public void stationaryMechTurnsTowardItsCombatTarget() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long target = sim.spawn(new EntitySpec("target", Faction.MARINE, UnitType.MARINE, 8, 5));
        sim.world().setTargetId(mech, target);

        new MechLocomotionSystem(sim.getEntityWorld(), c, sim.getRoster())
                .tick(BattleSimulation.TICK_DT);

        assertEquals(-174f, sim.getEntityWorld().getFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES), 0.001f);
        assertEquals(180f, sim.getEntityWorld().getFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY), 0.001f);
    }

    @Test
    public void mechanicalTravelPlantsAndBracesBetweenDriveStages() {
        assertEquals(0f, MechLocomotion.mechanicalTravelProgress(0.05f), 0.001f);
        assertTrue(MechLocomotion.mechanicalTravelProgress(0.10f) > 0f,
                "the shortened plant releases before 10% progress");
        assertEquals(0.55f, MechLocomotion.mechanicalTravelProgress(0.46f), 0.001f);
        assertEquals(0.55f, MechLocomotion.mechanicalTravelProgress(0.52f), 0.001f);
        assertEquals(1f, MechLocomotion.mechanicalTravelProgress(0.95f), 0.001f);
        assertEquals(1.15f, UnitType.HEAVY_MECH.moveSpeed, 0.001f);
    }
}
