package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A regenerating special brings back one use per its period, up to what it was issued, and no other item does. */
class SpecialRegenTest {
    private static final SpecialEquipmentDef FLAMER = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FLAMER_ID);
    private static final SpecialEquipmentDef ROCKET = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID);

    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(10, 10), null);
    private final World world = roster.world();

    @Test
    void aSpentUseComesBackAfterOnePeriod() {
        assertTrue(FLAMER.regenerates());
        long marine = spawn(FLAMER);
        world.setSecondaryAmmo(marine, 0);
        tick(marine, FLAMER.regenSeconds() - 2f * BattleSimulation.TICK_DT);
        assertEquals(0, world.secondaryAmmo(marine));
        tick(marine, 4f * BattleSimulation.TICK_DT);
        assertEquals(1, world.secondaryAmmo(marine));
    }

    @Test
    void aFullItemBanksNothing() {
        long marine = spawn(FLAMER);
        tick(marine, FLAMER.regenSeconds() * 3f);
        assertEquals(FLAMER.startingAmmo(), world.secondaryAmmo(marine));

        world.setSecondaryAmmo(marine, FLAMER.startingAmmo() - 1);
        tick(marine, FLAMER.regenSeconds() - 2f * BattleSimulation.TICK_DT);
        assertEquals(FLAMER.startingAmmo() - 1, world.secondaryAmmo(marine), "the idle time before the shot does not count");
    }

    @Test
    void anItemWithNoPeriodStaysSpent() {
        long marine = spawn(ROCKET);
        world.setSecondaryAmmo(marine, 0);
        tick(marine, 60f);
        assertEquals(0, world.secondaryAmmo(marine));
    }

    private void tick(long marine, float seconds) {
        for (int i = Math.round(seconds / BattleSimulation.TICK_DT); i > 0; i--) {
            InfantryUnitPrep.tickCooldowns(marine, world);
        }
    }

    private long spawn(SpecialEquipmentDef hand) {
        return roster.spawn(new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID))
                .specialEquipment(hand, hand.startingAmmo()));
    }
}
