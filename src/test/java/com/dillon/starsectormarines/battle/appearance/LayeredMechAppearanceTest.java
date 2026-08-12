package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechRole;
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

public class LayeredMechAppearanceTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        CellTopology topology = new CellTopology(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void onlyHeavyMechCarriesMechLayerState() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 7, 5));
        assertTrue(sim.getEntityWorld().has(mech, c.MECH_LAYERED_ANIMATION));
        assertTrue(sim.getEntityWorld().has(mech, c.MECH_LOCOMOTION));
        assertEquals(540f, sim.world().hp(mech), 0.001f);
        assertEquals(540f, sim.world().maxHp(mech), 0.001f);
        assertEquals(12, com.dillon.starsectormarines.battle.mech.MechWeapon.CHAINGUN.burstCount);
        assertEquals(30f, com.dillon.starsectormarines.battle.mech.MechWeapon.CHAINGUN.range,
                0.001f);
        assertFalse(sim.getEntityWorld().has(marine, c.MECH_LAYERED_ANIMATION));
        assertFalse(sim.getEntityWorld().has(marine, c.MECH_LOCOMOTION));
        assertEquals(LayeredMechAppearance.POD_HEAVY_SRM,
                sim.getEntityWorld().getInt(mech, c.MECH_LAYERED_ANIMATION,
                        BattleComponents.MECH_LAYERED_LEFT_SHOULDER));
        assertEquals(LayeredMechAppearance.POD_LRM,
                sim.getEntityWorld().getInt(mech, c.MECH_LAYERED_ANIMATION,
                        BattleComponents.MECH_LAYERED_RIGHT_SHOULDER));
    }

    @Test
    public void authorsMovementAndConcurrentWeaponTracks() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        long target = sim.spawn(new EntitySpec("target", Faction.MARINE, UnitType.MARINE, 8, 5));
        MechLoadoutComponent loadout = MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT);
        loadout.chaingunBurstRemaining = 4;
        loadout.chaingunBurstTimer = loadout.chaingun.burstSpacing * 0.5f;
        loadout.srmSalvoRemaining = 2;
        loadout.srmSalvoTimer = loadout.srmPod.burstSpacing * 0.25f;
        sim.world().attachMechLoadout(mech, loadout);
        sim.setPath(mech, new int[]{5, 5, 5, 6});
        sim.world().setMoveProgress(mech, 0.25f);
        sim.world().setTargetId(mech, target);

        new FacingSystem(sim.getEntityWorld(), c, sim.getRoster()).tick();

        assertEquals(-110f, sim.getEntityWorld().getFloat(mech, c.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_FACING_DEGREES), 0.001f);
        assertEquals(180f, sim.getEntityWorld().getFloat(mech, c.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_HIP_FACING_DEGREES), 0.001f);
        assertEquals(0.25f, sim.getEntityWorld().getFloat(mech, c.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_LOCOMOTION_PHASE), 0.001f);
        int flags = sim.getEntityWorld().getInt(mech, c.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_FLAGS);
        assertTrue((flags & LayeredMechAppearance.FLAG_MOVING) != 0);
        assertTrue((flags & LayeredMechAppearance.FLAG_CHAINGUN_ACTIVE) != 0);
        assertTrue((flags & LayeredMechAppearance.FLAG_SRM_ACTIVE) != 0);
        assertFalse((flags & LayeredMechAppearance.FLAG_LRM_ACTIVE) != 0);
    }

    @Test
    public void deathRemovesLiveMechLayersButKeepsLegacyCorpseSprite() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        sim.world().attachMechLoadout(mech, MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        assertTrue(sim.getEntityWorld().has(mech, c.MECH_LAYERED_ANIMATION));
        sim.applyDamage(mech, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT);
        assertFalse(sim.getEntityWorld().has(mech, c.MECH_LAYERED_ANIMATION));
        assertFalse(sim.getEntityWorld().has(mech, c.MECH_LOCOMOTION));
        assertTrue(sim.getEntityWorld().has(mech, c.SPRITE));
    }

    @Test
    public void authorsTurningSeparatelyFromForwardMovement() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long mech = sim.spawn(new EntitySpec("mech", Faction.DEFENDER, UnitType.HEAVY_MECH, 5, 5));
        sim.world().attachMechLoadout(mech,
                MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, -174f);
        sim.getEntityWorld().setFloat(mech, c.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY, 180f);

        new FacingSystem(sim.getEntityWorld(), c, sim.getRoster()).tick();

        int flags = sim.getEntityWorld().getInt(mech, c.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_FLAGS);
        assertTrue((flags & LayeredMechAppearance.FLAG_TURNING) != 0);
        assertFalse((flags & LayeredMechAppearance.FLAG_MOVING) != 0);
        assertTrue(sim.getEntityWorld().getFloat(mech, c.MECH_LAYERED_ANIMATION,
                BattleComponents.MECH_LAYERED_LOCOMOTION_PHASE) > 0f);
    }
}
