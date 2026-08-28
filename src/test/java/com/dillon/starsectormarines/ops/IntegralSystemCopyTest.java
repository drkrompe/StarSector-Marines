package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What the Armory promises about a suit's integral system, and the rule that it
 * stays a promise the simulation keeps ({@code integral-armor-systems.md}).
 */
class IntegralSystemCopyTest {

    /**
     * Most patterns carry nothing, and the line still has to say so. An absent
     * line is indistinguishable from one that failed to render, and the player
     * is choosing between patterns that mostly have nothing here.
     */
    @Test
    void aPatternCarryingNothingSaysSoRatherThanGoingBlank() {
        MarineArmorCatalogDef plain = MarineArmorCatalogRegistry.require("armor.field-fatigues");

        assertFalse(IntegralSystemCopy.carried(plain));
        assertEquals("No integral system", IntegralSystemCopy.summary(plain));
        assertTrue(IntegralSystemCopy.detail(plain).length() > 40,
                "the hover should explain the absence, not render empty");
    }

    @Test
    void theAuthoredRigNamesItsSystemAndHowOftenItIsAvailable() {
        MarineArmorCatalogDef rig = MarineArmorCatalogRegistry.require("armor.foundry-breaker");
        assertTrue(rig.hasIntegralSystem(),
                "fixture assumption: the foundry-breaker carries a breaching assist");

        String summary = IntegralSystemCopy.summary(rig);
        assertTrue(summary.contains(rig.integralSystem().displayName()), summary);
        assertTrue(summary.contains(seconds(rig.integralSystem().durationSeconds()) + "s")
                        && summary.contains(seconds(rig.integralSystem().cooldownSeconds()) + "s"),
                "a player deciding on the suit needs the authored clock, got: " + summary);

        String detail = IntegralSystemCopy.detail(rig);
        assertTrue(detail.contains(rig.integralSystem().description()), detail);
        assertTrue(detail.contains("before it can be spent again"), detail);
    }

    /**
     * The designer tile has room for the mechanics and not the prose. Its
     * authored description runs past two hundred characters, and a tile that
     * clips mid-sentence informs nobody.
     */
    @Test
    void theDesignerTileCarriesTheMechanicsRatherThanTheProse() {
        MarineArmorCatalogDef rig = MarineArmorCatalogRegistry.require("armor.foundry-breaker");

        String tile = IntegralSystemCopy.tile(rig);
        assertTrue(tile.length() <= 80, "a designer tile clips past ~80 chars, got: " + tile);
        assertFalse(tile.contains(rig.integralSystem().description()),
                "the full description belongs in the fire-team hover, not the tile");
        int boost = Math.round(
                (rig.integralSystem().breacherAssist().moveSpeedMult() - 1f) * 100f);
        assertTrue(tile.contains(boost + "%"), tile);
    }

    /**
     * The screen is evidence, not a brochure. The boost the Armory advertises is
     * the boost the simulation applies to the issued suit — if the two ever part
     * company, the visible-issue law ({@code progression-nouns.md}) is broken and
     * this fails.
     */
    @Test
    void theArmoryQuotesTheBoostTheSimulationActuallyApplies() {
        MarineArmorCatalogDef rig = MarineArmorCatalogRegistry.require("armor.foundry-breaker");
        MarineLoadout issued = MarineLoadout.fromCatalog(
                UnitRole.COMBATANT, null, null, EquipmentGrade.SERVICE,
                SoldierProfile.REGULAR, null, "marine-a", rig.appearanceFamily(),
                rig.armorCapacity(), rig.armorRating(), rig.moveSpeedMult(),
                rig.incomingAccuracyMult(), null, rig.integralSystem());

        UnitRosterService roster =
                new UnitRosterService(new UnitSpatialIndex(256, 256), null);
        EntitySpec spec = new EntitySpec("breacher", Faction.MARINE, UnitType.MARINE, 5, 5);
        issued.seedInto(spec);
        long id = roster.spawn(spec);

        IntegralSystemService systems = roster.integralSystems();
        float beforeActivation = roster.movement().moveSpeed(id);
        assertTrue(systems.activate(id));
        float whileRunning = roster.movement().moveSpeed(id);

        String advertised = Math.round((whileRunning / beforeActivation - 1f) * 100f) + "%";
        assertTrue(IntegralSystemCopy.detail(rig).contains(advertised),
                "the Armory should quote the measured " + advertised + " boost, but says: "
                        + IntegralSystemCopy.detail(rig));
    }

    /**
     * The ammunition path {@link IntegralSystemCopy} has always handled but
     * never had a live carrier for, until the missile pod
     * ({@code integral-armor-systems.md}). A player must be able to see
     * the remaining uses before issue, the same visibility law the cooldown
     * carriers already satisfy.
     */
    @Test
    void theArmoryShowsRemainingUsesForAnAmmunitionGatedSystem() {
        MarineArmorCatalogDef pattern = MarineArmorCatalogRegistry.require("armor.aegis-composite");
        assumeTrue(pattern.hasIntegralSystem(), "fixture assumption: the Aegis carries a system");
        IntegralSystemDef pod = pattern.integralSystem();
        assumeTrue(pod.effect() == IntegralSystemEffect.MISSILE_POD,
                "fixture assumption: the Aegis carries the missile pod");

        String summary = IntegralSystemCopy.summary(pattern);
        assertTrue(summary.contains(pod.displayName()), summary);
        assertTrue(summary.contains(pod.startingAmmo() + " uses")
                        || pod.startingAmmo() == 1 && summary.contains("1 use"),
                "the clock should quote the authored uses rather than a duration/cooldown pair: "
                        + summary);

        String detail = IntegralSystemCopy.detail(pattern);
        assertTrue(detail.contains(pod.description()), detail);
        assertTrue(detail.contains("the suit carries " + pod.startingAmmo()),
                "an ammunition-gated system should be described by its uses, not a cooldown: "
                        + detail);
    }

    /** Mirrors the copy's own formatting so the assertion tracks the catalog. */
    private static String seconds(float value) {
        return value == Math.rint(value)
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
