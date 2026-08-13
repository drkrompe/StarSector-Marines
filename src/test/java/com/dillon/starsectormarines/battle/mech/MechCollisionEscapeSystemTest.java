package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.SeparationSystem;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechCollisionEscapeSystemTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    @Test
    void stalledMechsTemporarilyIgnoreOnlyEachOthersSoftCollision() {
        BattleSimulation sim = arena();
        long first = sim.spawn(new EntitySpec("first", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long second = sim.spawn(new EntitySpec("second", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        MechLoadoutComponent firstLoadout = MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT);
        MechLoadoutComponent secondLoadout = MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT);
        sim.world().attachMechLoadout(first, firstLoadout);
        sim.world().attachMechLoadout(second, secondLoadout);
        sim.setPath(first, new int[]{5, 5, 8, 5});
        sim.setPath(second, new int[]{5, 5, 8, 5});
        assertEquals(firstLoadout, sim.world().mechLoadout(first));
        assertTrue(sim.world().pathIdx(first) < sim.world().path(first).length / 2);
        MechCollisionEscapeSystem escape = new MechCollisionEscapeSystem(
                sim.getEntityWorld(), sim.getBattleComponents());

        int ticks = (int) Math.ceil(MechCollisionEscapeSystem.STALL_ESCAPE_DELAY_SECONDS
                / BattleSimulation.TICK_DT) + 3;
        escape.tick(BattleSimulation.TICK_DT);
        assertEquals(8, firstLoadout.collisionProgressDestX);
        for (int i = 1; i < ticks; i++) escape.tick(BattleSimulation.TICK_DT);

        assertTrue(firstLoadout.collisionEscapeActive);
        assertTrue(secondLoadout.collisionEscapeActive);
        float x = sim.world().x(first);
        SeparationSystem separation = new SeparationSystem(sim.getRoster(), sim.getUnitIndex(), sim.getGrid());
        separation.tick(BattleSimulation.TICK_DT);
        assertEquals(x, sim.world().x(first), 0f,
                "escape hatch suppresses only the stalled mech-vs-mech separation push");

        // A meaningful advance immediately returns the pair to normal spacing.
        sim.world().setPos(first, x + MechCollisionEscapeSystem.PROGRESS_DISTANCE + 0.01f,
                sim.world().y(first));
        sim.clearPath(second);
        escape.tick(BattleSimulation.TICK_DT);

        assertFalse(firstLoadout.collisionEscapeActive);
        assertFalse(secondLoadout.collisionEscapeActive);
        float resumedX = sim.world().x(first);
        separation.tick(BattleSimulation.TICK_DT);
        assertNotEquals(resumedX, sim.world().x(first),
                "normal soft separation resumes as soon as either mech makes progress");
    }
}
