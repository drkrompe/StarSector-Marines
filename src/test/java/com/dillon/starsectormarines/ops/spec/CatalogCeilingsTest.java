package com.dillon.starsectormarines.ops.spec;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held ceilings must answer what the scans they replaced answered.
 *
 * <p>Each case re-runs the loop the two view models used to carry privately —
 * deliberately spelled out rather than delegated, since delegating would make
 * this a test that {@code CatalogCeilings} equals itself.
 */
class CatalogCeilingsTest {

    private static final SoldierProfile SHOOTER = SoldierProfile.REGULAR;

    @Test
    void weaponCeilingsMatchTheScansTheyReplaced() {
        float damage = 1f;
        float range = 1f;
        float dps = 1f;
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            if (weapon.mount != MountClass.MARINE_PRIMARY) continue;
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                damage = Math.max(damage, InfantryCombatStats.damage(weapon, grade));
                range = Math.max(range, InfantryCombatStats.range(weapon, grade));
                dps = Math.max(dps, InfantryCombatStats.estimatedDps(weapon, grade, SHOOTER));
            }
        }
        assertEquals(damage, CatalogCeilings.weaponDamage(), 0.0001f);
        assertEquals(range, CatalogCeilings.weaponRange(), 0.0001f);
        assertEquals(dps, CatalogCeilings.weaponDps(SHOOTER), 0.0001f);
        assertTrue(damage > 1f, "the shipped catalog has primaries to scan");
    }

    @Test
    void armourCeilingsMatchTheScansTheyReplaced() {
        float capacity = 1f;
        float rating = 1f;
        float move = 1f;
        for (MarineArmorCatalogDef armor : MarineArmorCatalogRegistry.installed().all()) {
            capacity = Math.max(capacity, armor.armorCapacity());
            rating = Math.max(rating, armor.armorRating());
            move = Math.max(move, armor.moveSpeedMult());
        }
        assertEquals(capacity, CatalogCeilings.armorCapacity(), 0.0001f);
        assertEquals(rating, CatalogCeilings.armorRating(), 0.0001f);
        assertEquals(move, CatalogCeilings.moveSpeedMult(), 0.0001f);
    }

    /**
     * Evasion is the one axis with no prior scan: it reads a suit's authored
     * multiplier on incoming accuracy from the side that says what the suit
     * denies, so the ceiling is the largest share anything denies.
     */
    @Test
    void evasionIsTheShareOfAnIncomingHitChanceASuitDenies() {
        float best = 0f;
        for (MarineArmorCatalogDef armor : MarineArmorCatalogRegistry.installed().all()) {
            best = Math.max(best, 1f - armor.incomingAccuracyMult());
            assertEquals(Math.max(0f, Math.min(1f, 1f - armor.incomingAccuracyMult())),
                    CatalogCeilings.evasionOf(armor), 0.0001f);
        }
        assertEquals(best, CatalogCeilings.armorEvasion(), 0.0001f);
        assertTrue(best > 0f, "some shipped pattern is harder to hit than a bare marine");
    }

    @Test
    void mechCeilingsAreTheEnumsOwnMaxima() {
        float structure = 1f;
        for (MechVariant variant : MechVariant.values()) {
            structure = Math.max(structure, variant.maxStructure);
        }
        assertEquals(structure, CatalogCeilings.mechStructure(), 0.0001f);
        assertEquals(MechVariant.HOUND.moveSpeed, CatalogCeilings.mechMoveSpeed(), 0.0001f);
    }

    /** A ceiling is held, so a second call is the same answer rather than a fresh scan. */
    @Test
    void resetDropsWhatWasHeldAndTheRescanAgrees() {
        float before = CatalogCeilings.weaponDamage();
        CatalogCeilings.reset();
        assertEquals(before, CatalogCeilings.weaponDamage(), 0.0001f);
    }
}
