package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechTurretSystemTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    @Test
    void torsoTraversesAtItsConfiguredRateAndBlocksFireUntilAligned() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long target = sim.spawn(new EntitySpec("target", Faction.MARINE, UnitType.MARINE, 7, 8));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT);
        loadout.torsoFacingDegrees = 0f;
        sim.world().attachMechLoadout(mech, loadout);
        sim.world().setTargetId(mech, target);
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
        MechTurretSystem turrets = new MechTurretSystem(
                sim.getEntityWorld(), c, sim.getRoster());

        turrets.tick(BattleSimulation.TICK_DT);

        assertEquals(-4f, loadout.torsoFacingDegrees, 0.001f,
                "120 deg/sec at 30 Hz gives a four-degree traverse step");
        assertFalse(loadout.isAimedAt(target));
        MechCombatantBehavior.tryFireChaingun(mech, loadout, target, 3.7f, sim, true);
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);
        assertEquals(0f, arms.cooldown, 0.001f,
                "a weapon must wait for the torso to traverse onto its target");

        for (int i = 0; i < 10; i++) turrets.tick(BattleSimulation.TICK_DT);

        assertTrue(loadout.isAimedAt(target));
        MechCombatantBehavior.tryFireChaingun(mech, loadout, target, 3.7f, sim, true);
        assertEquals(arms.weapon().cooldown, arms.cooldown, 0.001f);
    }
}
