package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
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

public class LayeredFacingSystemTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        CellTopology topology = new CellTopology(30, 30);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, topology);
    }

    private static float f(BattleSimulation sim, long id, int field) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getFloat(id, c.LAYERED_ANIMATION, field);
    }

    private static int i(BattleSimulation sim, long id, int field) {
        BattleComponents c = sim.getBattleComponents();
        return sim.getEntityWorld().getInt(id, c.LAYERED_ANIMATION, field);
    }

    @Test
    public void onlyConvertedInfantryCarryInfantryLayeredAnimation() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        for (UnitType type : UnitType.values()) {
            if (type.isStatic() || type.isDrone()) continue;
            long id = sim.spawn(new EntitySpec(type.name(), Faction.MARINE, type, 2 + type.ordinal(), 2));
            assertEquals(type.drawnAsLayers(), sim.getEntityWorld().has(id, c.LAYERED_ANIMATION), type.name());
        }
    }

    @Test
    public void armorSelectorsSeedIndependentlyFromUnitDefaults() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long blue = sim.spawn(new EntitySpec("blue", Faction.MARINE, UnitType.MARINE_BLUE, 5, 5));
        long red = sim.spawn(new EntitySpec("red", Faction.DEFENDER, UnitType.MARINE_RED, 7, 5));

        assertEquals(LayeredArmorFamily.BLUE_SCOUT.ordinal(),
                i(sim, blue, BattleComponents.LAYERED_BODY_FAMILY));
        assertEquals(LayeredArmorFamily.BLUE_SCOUT.ordinal(),
                i(sim, blue, BattleComponents.LAYERED_HEAD_FAMILY));
        assertEquals(LayeredArmorFamily.OUTLAW.ordinal(),
                i(sim, red, BattleComponents.LAYERED_BODY_FAMILY));

        sim.world().setLayeredHeadFamily(blue, LayeredArmorFamily.RED_ELITE);
        assertEquals(LayeredArmorFamily.BLUE_SCOUT, sim.world().layeredBodyFamily(blue));
        assertEquals(LayeredArmorFamily.RED_ELITE, sim.world().layeredHeadFamily(blue),
                "head can change without changing body selection");
    }

    @Test
    public void movingTorsoFollowsPathWhileHelmetLooksAtTarget() {
        BattleSimulation sim = arena();
        BattleComponents c = sim.getBattleComponents();
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 5, 5));
        long enemy = sim.spawn(new EntitySpec("e", Faction.DEFENDER, UnitType.MARINE, 8, 5));
        sim.setPath(marine, new int[]{5, 5, 5, 6});
        // The moving flag + travel bearing come from the velocity the mover
        // applied this tick, and the gait phase drives the locomotion pose;
        // poke the raw columns directly (no public mid-motion setters — the
        // mover owns them) to pin a deterministic northbound mid-stride state.
        sim.getEntityWorld().setFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE, 0.25f);
        sim.getEntityWorld().setFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, UnitType.MARINE.moveSpeed);
        sim.world().setTargetId(marine, enemy);

        new FacingSystem(sim.getEntityWorld(), sim.getBattleComponents(), sim.getRoster()).tick();

        assertEquals(0f, f(sim, marine, BattleComponents.LAYERED_FACING_DEGREES), 0.001f,
                "torso follows northbound path");
        assertEquals(-65f, f(sim, marine, BattleComponents.LAYERED_HEAD_LOOK_DEGREES), 0.001f,
                "helmet tracks east target, clamped relative to torso");
        assertEquals(0.25f, f(sim, marine, BattleComponents.LAYERED_LOCOMOTION_PHASE), 0.001f);
        assertTrue((i(sim, marine, BattleComponents.LAYERED_FLAGS)
                & LayeredAppearance.FLAG_MOVING) != 0);
    }

    @Test
    public void rocketSwitchesAboveShoulderOnlyAfterMidpointFire() {
        BattleSimulation sim = arena();
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 5, 5)
                .secondary(MarineSecondary.ROCKET_LAUNCHER, 3));
        long enemy = sim.spawn(new EntitySpec("e", Faction.DEFENDER, UnitType.HEAVY_MECH, 8, 5));
        sim.world().setTargetId(marine, enemy);
        sim.world().setSecondaryAimTargetId(marine, enemy);
        FacingSystem system = new FacingSystem(sim.getEntityWorld(), sim.getBattleComponents(), sim.getRoster());

        sim.world().setSecondaryActionTimer(marine, MarineSecondary.ROCKET_LAUNCHER.aimDuration * 0.75f);
        sim.world().setSecondaryFired(marine, false);
        system.tick();
        assertEquals(LayeredAppearance.POSE_ROCKET_AIM,
                i(sim, marine, BattleComponents.LAYERED_WEAPON_POSE));
        assertFalse((i(sim, marine, BattleComponents.LAYERED_FLAGS)
                & LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER) != 0);

        sim.world().setSecondaryActionTimer(marine, MarineSecondary.ROCKET_LAUNCHER.aimDuration * 0.49f);
        sim.world().setSecondaryFired(marine, true);
        system.tick();
        assertEquals(LayeredAppearance.POSE_ROCKET_FIRE,
                i(sim, marine, BattleComponents.LAYERED_WEAPON_POSE));
        assertTrue((i(sim, marine, BattleComponents.LAYERED_FLAGS)
                & LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER) != 0);
        assertTrue((i(sim, marine, BattleComponents.LAYERED_FLAGS)
                & LayeredAppearance.FLAG_MUZZLE_FLASH) != 0);
    }

    @Test
    public void layeredStateIsLiveOnlyAndLegacyCorpseSpriteSurvives() {
        BattleSimulation sim = arena();
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 5, 5));
        BattleComponents c = sim.getBattleComponents();
        assertTrue(sim.getEntityWorld().has(marine, c.LAYERED_ANIMATION));
        assertTrue(sim.getEntityWorld().has(marine, c.SPRITE));

        sim.applyDamage(marine, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(sim.getEntityWorld().has(marine, c.LAYERED_ANIMATION));
        assertTrue(sim.getEntityWorld().has(marine, c.SPRITE),
                "legacy SPRITE remains the corpse renderer contract");
    }
}
