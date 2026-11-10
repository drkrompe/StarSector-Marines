package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The machine flamers (`mechs-nouns.md`, Machine flamers): a burner's charge lands burning, and a chassis's fire control
 * holds one wherever the fire it lights could come back to its own side, as a pack flamer's throw is held.
 */
class MachineFlamerTest {

    @Test
    void aBurnerChargeLandsBurning() {
        Fixture f = new Fixture(null);
        f.fireAtTarget();
        assertEquals(1, f.shots.getActiveProjectiles().size(), "a charge leaves the burner");
        PendingDetonation charge = f.shots.getActiveProjectiles().get(0).onArrival;
        assertNotNull(charge, "the charge lands where it is aimed");
        assertTrue(charge.heat() > 0, "and lands burning");
    }

    @Test
    void aBurnerHoldsAChargeWhoseFireCouldComeBackToItsOwnSide() {
        // The burner stands four cells from its target: in a meadow the fire it lights comes back to it.
        Fixture meadow = new Fixture(FireFuel.GRASS.bit);
        meadow.fireAtTarget();
        assertTrue(meadow.shots.getActiveProjectiles().isEmpty(), "a meadow fire lit in the meadow it stands in");

        // On bare ground the same charge has nowhere to come back by.
        Fixture bare = new Fixture(0);
        bare.fireAtTarget();
        assertEquals(1, bare.shots.getActiveProjectiles().size());
    }

    @Test
    void everyMachineFlamerBurnsAndTheFamilySpansBothMountFamilies() {
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            if (component.machineFlamer()) assertTrue(component.weaponDef().incendiaryHeat() > 0, component.name());
        }
        assertFalse(MechWeaponComponent.FL_1.mountFamily == MechWeaponComponent.FL_2.mountFamily,
                "the family's two sizes take an arm or nose mount and a shoulder");
    }

    /** A Torch-armed Hound four cells south of a defender, on open floor, with the battle's fire if one is given. */
    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(64, 64);
        final UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        final UnitRosterService roster = new UnitRosterService(index, null);
        final ShotService shots = new ShotService();
        final HeavyWeapons weapons;
        final MechWeaponMount burner;
        final long shooter;
        final long target;

        /** {@code fuel} the fuel mask every cell carries, or null for a battle with no fire. */
        Fixture(Integer fuel) {
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
            shooter = roster.spawn(new EntitySpec("torch", Faction.MARINE, UnitType.HEAVY_MECH, 20, 20));
            target = roster.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, 20, 24));
            MechLoadoutComponent loadout = new MechLoadoutComponent(MechVariant.TORCH, MechRole.ASSAULT);
            loadout.torsoFacingDegrees = 0f;
            roster.world().attachMechLoadout(shooter, loadout);
            roster.entityWorld().setFloat(shooter, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, 0f);
            burner = loadout.mount(MechMountSlot.ARMS);
            Detonations detonations = new Detonations(roster, grid, new CellTopology(64, 64),
                    null, null, null, null, null);
            weapons = new HeavyWeapons(roster, grid,
                    new BallisticResolver(grid, new DoodadService(grid), index, roster), shots,
                    detonations, new Random() { @Override public float nextFloat() { return 0f; } });
            if (fuel != null) {
                FireField field = new FireField(64, 64, 1L, idx -> fuel);
                weapons.setFireField(() -> field);
            }
        }

        void fireAtTarget() {
            weapons.fireMechWeapon(shooter, target, burner, 1f);
        }
    }
}
