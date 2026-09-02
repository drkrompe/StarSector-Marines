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
     * Evasion reads a suit's authored multiplier on incoming accuracy from the
     * side that says what the suit denies, and keeps its sign: a pattern above
     * one is conspicuous enough to be easier to hit, which is half of what a
     * player weighs. A clamp here changed the armour-comparison card from -3%
     * to 0%, so this pins the sign rather than only the magnitude.
     */
    @Test
    void evasionIsSignedSoAConspicuousSuitStillSaysSo() {
        float best = 0.01f;
        int penalised = 0;
        for (MarineArmorCatalogDef armor : MarineArmorCatalogRegistry.installed().all()) {
            float evasion = 1f - armor.incomingAccuracyMult();
            best = Math.max(best, evasion);
            assertEquals(evasion, CatalogCeilings.evasionOf(armor), 0.0001f);
            if (evasion < 0f) penalised++;
        }
        assertEquals(best, CatalogCeilings.armorEvasion(), 0.0001f);
        assertTrue(best > 0f, "some shipped pattern is harder to hit than a bare marine");
        assertTrue(penalised > 0, "some shipped pattern is easier to hit, and says so");
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
