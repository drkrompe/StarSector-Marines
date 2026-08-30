package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/**
 * <b>How much a loadout is worth in the field, in the units the battle actually
 * settles in</b> — damage dealt per second, and damage stopped before a marine
 * is wounded ({@code role-and-access.md}).
 *
 * <p>Every number here is arithmetic over values the simulation itself reads:
 * {@link InfantryCombatStats} for output and {@link DurabilityModel} for what a
 * suit turns away. Nothing is a hand-authored quality label. That matters
 * because the alternative already exists and is not enough — a pattern's tier is
 * a price band and a definition's rarity is provenance, and neither can answer
 * "which of the four things I can afford right now is the best one".
 *
 * <p><b>What is deliberately not counted.</b> Special equipment, integral
 * systems, cover, morale, terrain and the order the squad is given all decide
 * battles, and none of them are in these numbers. Two of them have their own
 * line on the Armory tile for that reason: a sheet says what its twelve billets
 * <em>carry</em> beside what their plate <em>stops</em>, because a single scalar
 * that folded a field-aid satchel into a millimetre of ceramic would be an
 * invention rather than a measurement. The rating answers one question honestly
 * instead of every question vaguely.
 */
public final class LoadoutEffectiveness {

    /**
     * The marine a rating is quoted for. A rating is a comparison between
     * loadouts, so the wearer has to be held still; the ordinary regular is who
     * most billets are filled by.
     */
    private static final SoldierProfile REFERENCE_SHOOTER = SoldierProfile.REGULAR;

    /**
     * Where the reference engagement happens, as a share of the weapon's own
     * effective range. Half is the honest middle: quoting accuracy at the muzzle
     * would rate a shotgun above a marksman's rifle and quoting it at maximum
     * range would do the reverse. Each weapon is asked the same question about
     * its own reach rather than about an absolute distance it may not have.
     */
    private static final float REFERENCE_RANGE_FRACTION = 0.5f;

    private LoadoutEffectiveness() {}

    /**
     * Armour removed per second by one billet: what it fires, what actually
     * lands, and what gets through plate when it does.
     *
     * <p>All three terms are load-bearing and the measure is wrong without any
     * of them. Raw damage per second flatters a weapon that cannot hit, and the
     * grades that raise damage raise accuracy too, so the ladder between grades
     * comes out shallower than it plays if the two are not resolved together.
     * Penetration is the term it is most tempting to leave out and the one that
     * changes the answer most: the submachine gun puts out the highest raw
     * output in the catalog and strips a battlesuit at a sixth of the rate a
     * marksman's rifle does, because {@link DurabilityModel}'s efficiency floor
     * is a tenth and it is nowhere near that floor. A rating that scored the
     * submachine gun top would be recommending the wrong weapon in exactly the
     * fights the player loses.
     *
     * <p>Reach is <em>not</em> scored. Each weapon is asked how it does at half
     * of its own effective range, which measures how well it does its own job
     * and says nothing about whether it gets to do it — the marksman's rifle
     * opens at thirty-two cells and the submachine gun has to cross eighteen of
     * them first. That is a real advantage this number does not carry, and it is
     * left out rather than guessed at.
     */
    public static float effectiveDps(WeaponDef weapon, EquipmentGrade grade) {
        if (weapon == null || grade == null) return 0f;
        float dps = InfantryCombatStats.estimatedDps(weapon, grade, REFERENCE_SHOOTER);
        float hit = InfantryCombatStats.accuracyAtRangeFraction(
                weapon, grade, REFERENCE_SHOOTER, REFERENCE_RANGE_FRACTION);
        float through = DurabilityModel.armorEfficiency(
                weapon.penetration, referenceArmorRating());
        return dps * hit * through;
    }

    /**
     * How much incoming fire this pattern absorbs before its wearer starts
     * taking wounds, in damage points.
     *
     * <p>This is the plate's own arithmetic run backwards. Breaking armour of
     * capacity C at rating R costs {@code C / efficiency} damage, and a shot
     * that misses costs nothing at all — so a suit that is harder to hit is
     * literally more armour, which is why evasion belongs inside this number
     * rather than beside it.
     *
     * <p>Unpowered kit correctly rates zero: it has no capacity, so there is
     * nothing at all between its wearer and their own structure.
     */
    public static float patternResilience(MarineArmorCatalogDef pattern) {
        if (pattern == null || pattern.armorCapacity() <= 0f
                || pattern.armorRating() <= 0f) return 0f;
        float efficiency = DurabilityModel.armorEfficiency(
                referencePenetration(), pattern.armorRating());
        float absorbed = pattern.armorCapacity() / efficiency;
        float incomingAccuracy = Math.max(0.01f, pattern.incomingAccuracyMult());
        return absorbed / incomingAccuracy;
    }

    /**
     * The plate a weapon is expected to be shot at, taken as the mean armour
     * rating of every powered pattern the catalogs declare.
     *
     * <p>The dual of {@link #referencePenetration()}, and derived the same way
     * for the same reason: each side of the durability equation is scored
     * against the average of what it will actually meet, so neither a new
     * weapon nor a new suit silently misrates the other half of the armoury.
     */
    public static float referenceArmorRating() {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) return 1f;
        float total = 0f;
        int counted = 0;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            if (pattern.armorRating() <= 0f) continue;
            total += pattern.armorRating();
            counted++;
        }
        return counted == 0 ? 1f : Math.max(0.01f, total / counted);
    }

    /**
     * The fire a suit is expected to be under, taken as the mean penetration of
     * every infantry primary the catalogs declare.
     *
     * <p>Derived rather than chosen. A fixed constant would need re-tuning
     * whenever a weapon was added and would silently misrate every suit until
     * somebody noticed; a mean moves with the catalog, a contributed one
     * included, which is the rule the rest of the armoury already lives under.
     */
    public static float referencePenetration() {
        WeaponRegistry registry = WeaponRegistry.installed();
        if (registry == null) return 1f;
        float total = 0f;
        int counted = 0;
        for (WeaponDef weapon : registry.all()) {
            if (weapon.mount != MountClass.MARINE_PRIMARY) continue;
            total += weapon.penetration;
            counted++;
        }
        return counted == 0 ? 1f : Math.max(0.01f, total / counted);
    }

    /** Expected aimed output of all twelve billets, in damage per second. */
    public static float firepower(SquadWeaponDoctrine doctrine) {
        if (doctrine == null) return 0f;
        float total = 0f;
        for (SquadWeaponIssue issue : doctrine.issues()) {
            total += effectiveDps(issue.primaryDef(), issue.grade());
        }
        return total;
    }

    /** Damage all twelve suits absorb between them, in damage points. */
    public static float protection(SquadArmorDoctrine doctrine) {
        if (doctrine == null) return 0f;
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) return 0f;
        float total = 0f;
        for (String armorId : doctrine.issueIds()) {
            total += patternResilience(catalog.get(armorId));
        }
        return total;
    }

    /**
     * A weapon definition's firepower as a percentage of the most any twelve
     * billets could put out with what the catalog contains.
     *
     * <p>The ceiling is the catalog's rather than the company's on purpose: a
     * rating that rose because the player got poorer would be useless for the
     * one job it has. 100 would mean every billet carrying the hardest-hitting
     * primary at the best grade, which no real definition does — a definition
     * spends billets on marksmen and breachers, and its rating is what that
     * costs in output.
     */
    public static int weaponRating(SquadWeaponDoctrine doctrine) {
        return index(firepower(doctrine), MarineSquad.CAPACITY * bestEffectiveDps());
    }

    /** A tactic sheet's protection as a percentage of the best twelve suits catalogued. */
    public static int armorRating(SquadArmorDoctrine doctrine) {
        return index(protection(doctrine), MarineSquad.CAPACITY * bestResilience());
    }

    private static int index(float value, float ceiling) {
        if (!(ceiling > 0f) || !(value > 0f)) return 0;
        return Math.max(0, Math.min(100, Math.round(value / ceiling * 100f)));
    }

    private static float bestEffectiveDps() {
        WeaponRegistry registry = WeaponRegistry.installed();
        if (registry == null) return 0f;
        float best = 0f;
        for (WeaponDef weapon : registry.all()) {
            if (weapon.mount != MountClass.MARINE_PRIMARY) continue;
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                best = Math.max(best, effectiveDps(weapon, grade));
            }
        }
        return best;
    }

    private static float bestResilience() {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) return 0f;
        float best = 0f;
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            best = Math.max(best, patternResilience(pattern));
        }
        return best;
    }
}
