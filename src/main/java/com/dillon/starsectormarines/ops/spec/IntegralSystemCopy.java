package com.dillon.starsectormarines.ops.spec;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.BraceSpec;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.FieldAidSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.PerceptionSweepSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Screen copy for the capability an armour pattern carries in the suit itself
 * ({@code integral-armor-systems.md}).
 *
 * <p>Shared by every place a player weighs a suit — the doctrine designer where
 * the pattern is chosen, the fire-team card where the issue is reviewed, and the
 * barracks roll — so the same capability is described the same way wherever it
 * is read. A squad's fighting quality is fully determined by visible issue
 * ({@code progression-nouns.md}), and a capability nobody can read before
 * committing to it would be exactly the hidden modifier that law forbids.
 *
 * <p><b>This reports what the simulation applies, not what the catalog
 * declares.</b> A breacher assist authors a movement boost and a frontal
 * screen, and both now run — so both are quoted, including the arc, because a
 * screen a player believes is all-round is worse than no screen at all.
 * Anything a future system authors but does not yet simulate stays out of this
 * copy until it does; the Armory is evidence, not a brochure.
 */
public final class IntegralSystemCopy {

    private static final String SEPARATOR = "  ·  ";

    private IntegralSystemCopy() { }

    public static boolean carried(MarineArmorCatalogDef armor) {
        return armor != null && armor.hasIntegralSystem();
    }

    /**
     * One line for a card or a cycling button: what it is and how often it is
     * available. Sized to sit where a carried special item's name sits, because
     * that symmetry is the point — a suit's system and a billet's special item
     * are separate issues, and the screen should not imply one costs the other.
     */
    public static String summary(MarineArmorCatalogDef armor) {
        if (!carried(armor)) return Strings.get("gearSystemNone");
        IntegralSystemDef system = armor.integralSystem();
        return system.familyName() + SEPARATOR + system.grade().displayName()
                + SEPARATOR + clock(system);
    }

    /**
     * What this tradition calls its own version. Six patterns carry a breach
     * assist and every one of them named it something else, which is good
     * flavour and useless for comparison — so the family and the grade lead,
     * and the authored name follows as the thing it is.
     */
    public static String flavorName(MarineArmorCatalogDef armor) {
        return carried(armor) ? armor.integralSystem().displayName() : "";
    }

    /** The family icon, or null when the pattern carries nothing. */
    public static String iconPath(MarineArmorCatalogDef armor) {
        return carried(armor) ? armor.integralSystem().effect().iconPath : null;
    }

    /**
     * One compact line for a tile that has room for the mechanics but not the
     * prose: what it is, how often, and what it does. The authored description
     * is deliberately left out — it runs to a couple of hundred characters and
     * would clip rather than inform. That belongs in {@link #detail}.
     */
    public static String tile(MarineArmorCatalogDef armor) {
        if (!carried(armor)) return summary(armor);
        String effect = effect(armor.integralSystem());
        return effect.isEmpty() ? summary(armor)
                : summary(armor) + SEPARATOR + effect;
    }

    /** The hover copy: the authored description, then what it does and its clock. */
    public static String detail(MarineArmorCatalogDef armor) {
        List<String> parts = new ArrayList<>(detailParagraphs(armor));
        if (carried(armor)) parts.add(0, armor.integralSystem().displayName());
        return String.join("  //  ", parts);
    }

    /**
     * {@link #detail} as paragraphs, for a surface that names the system in its
     * own heading and wants the note without the name repeated.
     */
    public static List<String> detailParagraphs(MarineArmorCatalogDef armor) {
        if (!carried(armor)) {
            return List.of(Strings.get("gearSystemNoneDetail"));
        }
        IntegralSystemDef system = armor.integralSystem();
        List<String> parts = new ArrayList<>();
        parts.add(system.description());
        String effect = effectDetail(system);
        if (!effect.isEmpty()) parts.add(effect);
        parts.add(availability(system));
        return List.copyOf(parts);
    }

    /**
     * What the suit measurably does while the system runs, sized for a tile.
     * Only effects the simulation actually applies appear here — see the class
     * note. Terse on purpose: a designer tile clips past roughly eighty
     * characters, and the pattern name and its clock have already spent half of
     * that.
     */
    private static String effect(IntegralSystemDef system) {
        return effectCopy(system, false);
    }

    private static String effectDetail(IntegralSystemDef system) {
        return effectCopy(system, true);
    }

    /** Complete messages keep word order, units and tradeoffs under translator control. */
    private static String effectCopy(IntegralSystemDef system, boolean detail) {
        String suffix = detail ? "Detail" : "";
        BreacherAssistSpec breacher = system.breacherAssist();
        if (breacher != null) {
            return Strings.format("gearSystemBreach" + (system.grantsMitigation() ? "Screen" : "") + suffix,
                    percent(breacher.moveSpeedMult() - 1f), Math.round(breacher.screenSoak()),
                    degrees(breacher.shieldedArcDegrees()));
        }
        MissilePodSpec pod = system.missilePod();
        if (pod != null) {
            return Strings.format("gearSystemMissiles" + suffix,
                    Math.max(1, pod.weaponDef().projectilesPerShot()));
        }
        PerceptionSweepSpec sweep = system.perceptionSweep();
        if (sweep != null) {
            return Strings.format("gearSystemSweep" + (sweep.wallReadRadiusCells() > 0f ? "Walls" : "") + suffix,
                    Math.round(sweep.revealRangeCells()), Math.round(sweep.wallReadRadiusCells()));
        }
        BraceSpec brace = system.brace();
        if (brace != null) {
            return Strings.format("gearSystemBrace" + suffix,
                    percent(brace.accuracyMult() - 1f), percent(1f - brace.moveSpeedMult()));
        }
        FieldAidSpec aid = system.fieldAid();
        if (aid != null) {
            return Strings.format("gearSystemAid" + suffix, Math.round(aid.restoredHealth()),
                    Math.round(aid.reachCells()), percent(aid.treatBelowHealthFraction()));
        }
        return "";
    }

    /** Whole degrees with the sign; an arc is authored to the degree and read at a glance. */
    private static String degrees(float value) {
        return Math.round(value) + "°";
    }

    private static String availability(IntegralSystemDef system) {
        return system.usesAmmunition()
                ? Strings.format("gearSystemAvailabilityAmmo", seconds(system.durationSeconds()), system.startingAmmo())
                : Strings.format("gearSystemAvailabilityCooldown", seconds(system.durationSeconds()), seconds(system.cooldownSeconds()));
    }

    /** The compact availability clock: {@code 3.0s / 22s}, or {@code 2 uses}. */
    private static String clock(IntegralSystemDef system) {
        return system.usesAmmunition()
                ? uses(system.startingAmmo())
                : Strings.format("gearSystemClock", seconds(system.durationSeconds()), seconds(system.cooldownSeconds()));
    }

    private static String uses(int ammo) {
        return Strings.format("gearUses", ammo);
    }

    private static String percent(float fraction) {
        return Math.round(fraction * 100f) + "%";
    }

    /** Whole seconds lose their decimal; a 3.5s window keeps it. */
    private static String seconds(float value) {
        return value == Math.rint(value)
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
