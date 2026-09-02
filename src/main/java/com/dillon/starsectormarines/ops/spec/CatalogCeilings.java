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
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The best the installed catalogs can do on each axis a comparison meter uses.
 *
 * <p>A meter is measured against the whole catalog rather than against whatever
 * else is on screen, so cycling one item produces a meaningful change and
 * selecting a different team cannot rewrite the baseline
 * ({@code company-view-nouns.md}). Every screen that draws such a meter asks
 * here; the scans used to exist twice, once per view model, and were re-run for
 * every card built.
 *
 * <p>Computed lazily and held, because a catalog does not change while the game
 * runs. {@link #reset()} exists for a test that installs a different catalog
 * mid-run; nothing in the game calls it.
 *
 * <p><b>A ceiling is per weapon population, not per number.</b> Marine primaries
 * and the guns behind special items are separate scans: a rocket outranges every
 * rifle, and measuring one against the other's ceiling would peg or flatten every
 * bar it drew.
 */
public final class CatalogCeilings {

    /**
     * Sustained output depends on the shooter as well as the weapon, so this one
     * ceiling is keyed by profile. Small and bounded — the screens quote one or
     * two profiles — and cleared with everything else by {@link #reset()}.
     */
    private static final Map<SoldierProfile, Float> WEAPON_DPS = new ConcurrentHashMap<>();

    private static volatile float weaponDamage;
    private static volatile float weaponRange;
    private static volatile float specialDamage;
    private static volatile float specialRange;
    private static volatile float armorCapacity;
    private static volatile float armorRating;
    private static volatile float moveSpeedMult;
    private static volatile float armorEvasion;

    private CatalogCeilings() { }

    /** Highest grade-scaled damage any marine primary reaches. */
    public static float weaponDamage() {
        float cached = weaponDamage;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (WeaponDef weapon : marinePrimaries()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                scanned = Math.max(scanned, InfantryCombatStats.damage(weapon, grade));
            }
        }
        weaponDamage = scanned;
        return scanned;
    }

    /** Longest grade-scaled reach any marine primary has. */
    public static float weaponRange() {
        float cached = weaponRange;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (WeaponDef weapon : marinePrimaries()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                scanned = Math.max(scanned, InfantryCombatStats.range(weapon, grade));
            }
        }
        weaponRange = scanned;
        return scanned;
    }

    /** Highest sustained output any marine primary reaches in this shooter's hands. */
    public static float weaponDps(SoldierProfile profile) {
        SoldierProfile key = profile != null ? profile : SoldierProfile.REGULAR;
        return WEAPON_DPS.computeIfAbsent(key, shooter -> {
            float scanned = 1f;
            for (WeaponDef weapon : marinePrimaries()) {
                for (EquipmentGrade grade : EquipmentGrade.values()) {
                    scanned = Math.max(scanned,
                            InfantryCombatStats.estimatedDps(weapon, grade, shooter));
                }
            }
            return scanned;
        });
    }

    /**
     * Hardest hit any special item's weapon lands. Specials fire at their
     * authored weapon values with no equipment grade applied, so this scan takes
     * none either — see {@link SpecSheets#special}.
     */
    public static float specialDamage() {
        float cached = specialDamage;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (SpecialEquipmentDef special : specialWeapons()) {
            scanned = Math.max(scanned, special.damage());
        }
        specialDamage = scanned;
        return scanned;
    }

    /** Longest reach any special item's weapon has. */
    public static float specialRange() {
        float cached = specialRange;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (SpecialEquipmentDef special : specialWeapons()) {
            scanned = Math.max(scanned, special.range());
        }
        specialRange = scanned;
        return scanned;
    }

    /** Deepest armour pool any pattern carries. */
    public static float armorCapacity() {
        float cached = armorCapacity;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (MarineArmorCatalogDef armor : armorPatterns()) {
            scanned = Math.max(scanned, armor.armorCapacity());
        }
        armorCapacity = scanned;
        return scanned;
    }

    /** Best per-hit resistance any pattern carries. */
    public static float armorRating() {
        float cached = armorRating;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (MarineArmorCatalogDef armor : armorPatterns()) {
            scanned = Math.max(scanned, armor.armorRating());
        }
        armorRating = scanned;
        return scanned;
    }

    /** Fastest a pattern lets its wearer move, as a multiplier on the marine's own speed. */
    public static float moveSpeedMult() {
        float cached = moveSpeedMult;
        if (cached > 0f) return cached;
        float scanned = 1f;
        for (MarineArmorCatalogDef armor : armorPatterns()) {
            scanned = Math.max(scanned, armor.moveSpeedMult());
        }
        moveSpeedMult = scanned;
        return scanned;
    }

    /**
     * The largest share of an incoming hit chance any pattern denies.
     *
     * <p>The floor is a hundredth rather than zero purely so the division has a
     * denominator; a catalog in which nothing evades has no evasion meter worth
     * drawing either way.
     */
    public static float armorEvasion() {
        float cached = armorEvasion;
        if (cached > 0f) return cached;
        float scanned = 0.01f;
        for (MarineArmorCatalogDef armor : armorPatterns()) {
            scanned = Math.max(scanned, evasionOf(armor));
        }
        armorEvasion = scanned;
        return scanned;
    }

    /**
     * What share of an incoming hit chance this pattern denies.
     *
     * <p>A suit's authored value is a multiplier on the shooter's chance to hit
     * the wearer, so the part worth reading is what it takes away — and
     * <b>this is signed on purpose</b>. Three shipped patterns are above one:
     * a lashplate harness, a packframe and the Lion's Mantle are conspicuous
     * enough to be <em>easier</em> to hit than a bare marine, and that penalty
     * is half of what a player is weighing. Clamping it to zero was tried and
     * changed the armour-comparison card from "-3%" to "0%", which is the
     * brochure this copy exists not to be. The meter's own fill clamps; the
     * number does not.
     */
    public static float evasionOf(MarineArmorCatalogDef armor) {
        return armor == null ? 0f : 1f - armor.incomingAccuracyMult();
    }

    /** Toughest chassis in the mech catalog. */
    public static float mechStructure() {
        return maxOverVariants(variant -> variant.maxStructure);
    }

    /** Deepest armour pool in the mech catalog. */
    public static float mechArmorCapacity() {
        return maxOverVariants(variant -> variant.armorCapacity);
    }

    /** Fastest chassis in the mech catalog. */
    public static float mechMoveSpeed() {
        return maxOverVariants(variant -> variant.moveSpeed);
    }

    /** Steadiest chassis in the mech catalog. */
    public static float mechAccuracy() {
        return maxOverVariants(variant -> variant.accuracy);
    }

    /** Furthest-seeing chassis in the mech catalog. */
    public static float mechVisionRange() {
        return maxOverVariants(variant -> variant.visionRange);
    }

    /** Drops every held scan. For a test that installs a different catalog mid-run. */
    public static void reset() {
        weaponDamage = 0f;
        weaponRange = 0f;
        specialDamage = 0f;
        specialRange = 0f;
        armorCapacity = 0f;
        armorRating = 0f;
        moveSpeedMult = 0f;
        armorEvasion = 0f;
        WEAPON_DPS.clear();
    }

    private static float maxOverVariants(Function<MechVariant, Float> axis) {
        float scanned = 1f;
        for (MechVariant variant : MechVariant.values()) {
            scanned = Math.max(scanned, axis.apply(variant));
        }
        return scanned;
    }

    private static Iterable<WeaponDef> marinePrimaries() {
        List<WeaponDef> primaries = new ArrayList<>();
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            if (weapon.mount == MountClass.MARINE_PRIMARY) primaries.add(weapon);
        }
        return primaries;
    }

    private static Iterable<SpecialEquipmentDef> specialWeapons() {
        List<SpecialEquipmentDef> armed = new ArrayList<>();
        SpecialEquipmentRegistry registry = SpecialEquipmentRegistry.installed();
        if (registry == null) return armed;
        for (SpecialEquipmentDef special : registry.all()) {
            if (special.weaponId() != null && !special.weaponId().isBlank()) armed.add(special);
        }
        return armed;
    }

    private static Iterable<MarineArmorCatalogDef> armorPatterns() {
        MarineArmorCatalogRegistry registry = MarineArmorCatalogRegistry.installed();
        return registry == null ? List.of() : registry.all();
    }
}
