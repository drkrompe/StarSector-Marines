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

class MechTargetingTest {

    @Test
    void replacesRearTargetWithEnemyInsideCurrentTraverse() {
        BattleSimulation sim = arena();
        long mech = mechFacingNorth(sim);
        long rearTarget = alien(sim, 20, 8);
        long flankTarget = alien(sim, 12, 20);
        sim.world().setTargetId(mech, rearTarget);

        assertEquals(flankTarget, MechTargeting.refreshTarget(mech, sim),
                "a visible target inside the gun arc should replace one in the rear blind wedge");
    }

    @Test
    void closeThreatInterruptsAValidDistantEngagement() {
        BattleSimulation sim = arena();
        long mech = mechFacingNorth(sim);
        long distantTarget = alien(sim, 20, 35);
        long closeThreat = alien(sim, 26, 20);
        sim.world().setTargetId(mech, distantTarget);

        assertEquals(closeThreat, MechTargeting.refreshTarget(mech, sim),
                "an enemy inside the self-defense radius should pull aggro from a distant target");
    }

    @Test
    void keepsAnExistingCloseTargetToAvoidThrashing() {
        BattleSimulation sim = arena();
        long mech = mechFacingNorth(sim);
        long current = alien(sim, 27, 20);
        alien(sim, 24, 20);
        sim.world().setTargetId(mech, current);

        assertEquals(current, MechTargeting.refreshTarget(mech, sim),
                "once fighting at close range, the mech should finish traversing instead of changing every tick");
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(45, 45);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(45, 45));
    }

    private static long mechFacingNorth(BattleSimulation sim) {
        long mech = sim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 20, 20));
        BattleComponents components = sim.getBattleComponents();
        sim.getEntityWorld().setFloat(mech, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        return mech;
    }

    private static long alien(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("alien-" + sim.liveUnitCount(),
                Faction.DEFENDER, UnitType.ALIEN, x, y));
    }
}
