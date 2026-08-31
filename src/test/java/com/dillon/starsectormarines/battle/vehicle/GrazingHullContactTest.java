package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A round fired past the flank of a chassis at point-blank range must contact
 * its hull. The broad phase gathers candidates whose CENTRE lies within a
 * margin of the ray, so the margin has to be at least the largest body radius
 * in the game — an APC's is 1.2 cells against a 1.0-cell base margin, and the
 * speed term that tops it up shrinks to nothing over a two-cell shot.
 */
class GrazingHullContactTest {

    private static final class MidRoll extends Random {
        @Override public float nextFloat() { return 0.5f; }
        @Override public double nextDouble() { return 0.5d; }
    }

    @Test
    void aPointBlankRoundAlongTheFlankContactsTheHull() {
        NavigationGrid grid = new NavigationGrid(24, 14);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()), 7L);

        // The chassis sits with its centre 1.15 cells off the firing line —
        // inside its own 1.2-cell hull radius, so the exact contact test says
        // the round strikes it.
        VehicleMission mission = new VehicleMission(
                new float[]{10.5f, 11.5f}, new float[]{6.65f, 6.65f},
                new float[]{11.5f, 10.5f}, new float[]{6.65f, 6.65f},
                0f, VehicleType.HEAVY_APC.capacity);
        mission.state = VehicleState.INCOMING;
        long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);

        long shooter = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 9, 5));
        sim.world().setPos(shooter, 9.5f, 5.5f);
        // A two-cell shot straight down the lane the hull overhangs.
        long mark = sim.spawn(new EntitySpec("mark", Faction.DEFENDER,
                UnitType.MILITIA, 11, 5));
        sim.world().setPos(mark, 11.5f, 5.5f);

        BallisticResolver resolver = new BallisticResolver(sim.getGrid(),
                new DoodadService(sim.getGrid()), sim.getUnitIndex(), sim.getRoster());
        BallisticResolver.Resolution shot = resolver.resolve(shooter, mark,
                1f, 0f, 48f, 12f, new MidRoll());

        assertEquals(apc, shot.victimId(),
                "the hull overhangs the lane, so the round stops on it");
    }
}
