package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Armory's rating actually measures ({@code role-and-access.md}).
 *
 * <p>Nothing here asserts a shipped number. Each case changes exactly one input
 * on an otherwise identical pair and states which way the answer must move,
 * because the claim being made is about the measure rather than about the
 * catalog it is currently pointed at.
 */
class LoadoutEffectivenessTest {

    /**
     * The term it is most tempting to leave out. Two weapons with identical
     * output, one of which cannot get through plate, are not worth the same to
     * a squad that fights armoured infantry — and raw damage per second, which
     * is what a rating usually means, cannot tell them apart at all.
     */
    @Test
    void aRoundThatCannotGetThroughPlateIsWorthLessThanOneThatCan() throws Exception {
        WeaponDef blunt = weapon("weapon.test-blunt", 1.0, 0.5);
        WeaponDef sharp = weapon("weapon.test-sharp", 20.0, 0.5);

        assertTrue(LoadoutEffectiveness.effectiveDps(sharp, EquipmentGrade.SERVICE)
                        > LoadoutEffectiveness.effectiveDps(blunt, EquipmentGrade.SERVICE),
                "identical output, and only one of them opens a suit");
    }

    /** A weapon that cannot hit is worth nothing however hard it lands. */
    @Test
    void aWeaponThatMissesIsNotWorthWhatItFires() throws Exception {
        WeaponDef wild = weapon("weapon.test-wild", 5.0, 0.2);
        WeaponDef steady = weapon("weapon.test-steady", 5.0, 0.8);

        assertTrue(LoadoutEffectiveness.effectiveDps(steady, EquipmentGrade.SERVICE)
                > LoadoutEffectiveness.effectiveDps(wild, EquipmentGrade.SERVICE));
    }

    /**
     * A shot that misses costs nothing, so a suit that is harder to hit is
     * literally more armour. Two patterns with the same plate and different
     * profiles are not equally protective.
     */
    @Test
    void aSuitThatIsHarderToHitAbsorbsMoreThanItsPlateAlone() {
        MarineArmorCatalogDef exposed = suit(10f, 8f, 1.0f);
        MarineArmorCatalogDef low = suit(10f, 8f, 0.8f);

        assertTrue(LoadoutEffectiveness.patternResilience(low)
                > LoadoutEffectiveness.patternResilience(exposed));
    }

    /** Thicker plate at the same rating absorbs proportionally more. */
    @Test
    void twiceTheCapacityAtTheSameRatingAbsorbsTwiceAsMuch() {
        float thin = LoadoutEffectiveness.patternResilience(suit(6f, 8f, 1.0f));
        float thick = LoadoutEffectiveness.patternResilience(suit(12f, 8f, 1.0f));

        assertEquals(thin * 2f, thick, thin * 0.001f);
    }

    /** Unpowered kit is the absence of a suit, and puts nothing between anybody and a round. */
    @Test
    void unpoweredKitAbsorbsNothing() {
        assertEquals(0f, LoadoutEffectiveness.patternResilience(suit(0f, 0f, 1.0f)));
    }

    /**
     * The index is a share of the catalog's own ceiling, so it is bounded and
     * an empty loadout cannot be flattered by a poor catalog.
     */
    @Test
    void aRatingStaysInsideItsScale() {
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            int rating = LoadoutEffectiveness.weaponRating(doctrine);
            assertTrue(rating >= 0 && rating <= 100, doctrine.id() + " rated " + rating);
        }
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            int rating = LoadoutEffectiveness.armorRating(
                    ArmorIssueResolver.resolveUnrestricted(plan));
            assertTrue(rating >= 0 && rating <= 100, plan.id() + " rated " + rating);
        }
        assertEquals(0, LoadoutEffectiveness.weaponRating(null));
        assertEquals(0, LoadoutEffectiveness.armorRating(null));
    }

    /** The deployment roster quotes the same bounded scale for one billet. */
    @Test
    void anIndividualBilletUsesTheCatalogScale() {
        WeaponDef primary = WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID);
        MarineArmorCatalogDef armor = MarineArmorCatalogRegistry.require(
                MarineArmorPattern.ARMORLESS.id);

        int firepower = LoadoutEffectiveness.billetWeaponRating(
                primary, EquipmentGrade.SERVICE);
        int protection = LoadoutEffectiveness.billetArmorRating(armor);

        assertTrue(firepower >= 0 && firepower <= 100);
        assertTrue(protection >= 0 && protection <= 100);
    }

    /**
     * A sheet that fields better plate rates higher than the same sheet fielded
     * from a poorer hold. This is the whole point of the number on the tile: it
     * answers "what would this actually put in the field", not "how good is the
     * idea".
     */
    @Test
    void theSamePlanRatesHigherWhenTheCompanyCanFillItBetter() {
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlans().get(0);
        SquadArmorDoctrine rich = ArmorIssueResolver.resolveUnrestricted(plan);
        SquadArmorDoctrine poor = ArmorIssueResolver.resolve(plan,
                pattern -> pattern.tier() <= 1);

        assertTrue(LoadoutEffectiveness.armorRating(rich)
                        > LoadoutEffectiveness.armorRating(poor),
                "a plan does not get better; the kit filling it does");
    }

    // ---------------------------------------------------------------- fixture

    private static MarineArmorCatalogDef suit(
            float capacity, float rating, float incomingAccuracy) {
        return new MarineArmorCatalogDef("armor.test", "Test suit", ArmorRole.LINE,
                ArmorTradition.HEGEMONY, "A test suit.", 1,
                "graphics/ui/armory/armor-tier-1-light.png", LayeredArmorFamily.ARMY_GREEN,
                capacity, rating, 1f, incomingAccuracy, null);
    }

    private static WeaponDef weapon(String id, double penetration, double accuracy)
            throws Exception {
        return WeaponDef.parse(new JSONObject("""
                {
                  "id": "%s",
                  "mount": "marine-primary",
                  "catalog": {
                    "displayName": "Test Rifle", "modelName": "Test",
                    "designation": "TST", "designationTiered": false,
                    "role": "LINE", "description": "A test primary."
                  },
                  "sim": {
                    "range": 20.0, "damage": 10.0, "accuracy": %s,
                    "cooldown": 1.0, "penetration": %s
                  },
                  "render": { "heldSpriteFamily": "RIFLE" },
                  "fx": { "impact": [ { "kind": "glow", "radius": 0.5, "lifetime": 0.1 } ] }
                }
                """.formatted(id, accuracy, penetration)));
    }
}
