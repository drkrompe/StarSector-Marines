package com.dillon.starsectormarines.ops.spec;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
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
        if (!carried(armor)) return "No integral system";
        IntegralSystemDef system = armor.integralSystem();
        return system.familyName() + SEPARATOR + system.grade().displayName
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
                : summary(armor) + SEPARATOR + effect + ".";
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
            return List.of("This pattern carries no integral system. It fights on its plate, its"
                    + " balance, and whatever the billet is carrying.");
        }
        IntegralSystemDef system = armor.integralSystem();
        List<String> parts = new ArrayList<>();
        parts.add(system.description());
        String effect = effectDetail(system);
        if (!effect.isEmpty()) {
            // Only a reverting effect (a temporary stat boost) is honestly
            // described as "the suit is exactly the suit it was" afterward —
            // a spent salvo doesn't revert anything, it's just gone.
            parts.add(system.breacherAssist() != null || system.perceptionSweep() != null
                    || system.brace() != null
                    ? effect + ", then the suit is exactly the suit it was."
                    : effect + ".");
        }
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
        BreacherAssistSpec breacher = system.breacherAssist();
        if (breacher != null) {
            String movement = percent(breacher.moveSpeedMult() - 1f) + " faster";
            return system.grantsMitigation()
                    ? movement + ", " + Math.round(breacher.screenSoak()) + " soak over "
                            + degrees(breacher.shieldedArcDegrees())
                    : movement;
        }
        MissilePodSpec pod = system.missilePod();
        if (pod != null) {
            int salvo = Math.max(1, pod.weaponDef().projectilesPerShot());
            return "Fires " + salvo + (salvo == 1 ? " missile" : " missiles") + " on its own";
        }
        PerceptionSweepSpec sweep = system.perceptionSweep();
        if (sweep != null) {
            return "Reads " + cells(sweep.revealRangeCells()) + " out"
                    + (sweep.wallReadRadiusCells() > 0f
                            ? ", " + cells(sweep.wallReadRadiusCells()) + " of it through walls"
                            : "");
        }
        BraceSpec brace = system.brace();
        if (brace != null) {
            // Both halves or neither. A stance quoted only for what it buys is
            // the brochure this class exists not to be, and the cost is the
            // part a player has to weigh.
            return percent(brace.accuracyMult() - 1f) + " steadier, "
                    + percent(1f - brace.moveSpeedMult()) + " slower";
        }
        FieldAidSpec aid = system.fieldAid();
        if (aid != null) {
            return "Puts " + damage(aid.restoredHealth()) + " back, "
                    + cells(aid.reachCells()) + " reach";
        }
        return "";
    }

    /**
     * The same effects with room to say them properly. The arc is spelled out
     * rather than implied: the screen faces one way, and a player who reads it
     * as all-round will walk a heavy suit into a flanking gun.
     */
    private static String effectDetail(IntegralSystemDef system) {
        BreacherAssistSpec breacher = system.breacherAssist();
        if (breacher != null) {
            String movement = "Moves " + percent(breacher.moveSpeedMult() - 1f)
                    + " faster while it runs";
            return system.grantsMitigation()
                    ? movement + " and raises a screen that soaks "
                            + damage(breacher.screenSoak())
                            + " of damage across its " + degrees(breacher.shieldedArcDegrees())
                            + " front before it breaks, leaving the flanks exactly as exposed"
                            + " as they were"
                    : movement;
        }
        MissilePodSpec pod = system.missilePod();
        if (pod != null) {
            WeaponDef weapon = pod.weaponDef();
            int salvo = Math.max(1, weapon.projectilesPerShot());
            return "Looses a salvo of " + salvo
                    + (salvo == 1 ? " micro-missile" : " micro-missiles")
                    + " at a target the pod picks for itself, with the ordinary blast and the"
                    + " ordinary consequences for anyone standing near it";
        }
        PerceptionSweepSpec sweep = system.perceptionSweep();
        if (sweep != null) {
            String read = "Opens " + cells(sweep.revealRangeCells())
                    + " of ground around the wearer to your own picture";
            // The wall read is the half worth spelling out, because it is the
            // half that is bounded: a reader who takes it for an x-ray will
            // walk a scout up to a block expecting the far side of it.
            return sweep.wallReadRadiusCells() > 0f
                    ? read + ", carrying through walls within "
                            + cells(sweep.wallReadRadiusCells()) + " of them and no further"
                    : read + ", stopped by every wall in the way";
        }
        BraceSpec brace = system.brace();
        if (brace != null) {
            return "Plants: while it holds, the wearer's fire is "
                    + percent(brace.accuracyMult() - 1f) + " steadier and they move "
                    + percent(1f - brace.moveSpeedMult()) + " slower. The cost is the"
                    + " whole point -- a marine braced in the wrong place is committed"
                    + " to being there";
        }
        FieldAidSpec aid = system.fieldAid();
        if (aid != null) {
            return "Kneels beside a squadmate within " + cells(aid.reachCells())
                    + " who is below " + percent(aid.treatBelowHealthFraction())
                    + " of their health and puts " + damage(aid.restoredHealth())
                    + " back on their feet, out of a satchel that does not refill."
                    + " The wearer never treats themselves";
        }
        return "";
    }

    /** Whole cells; a range is authored to the cell and read as a distance. */
    private static String cells(float value) {
        return Math.round(value) + " cells";
    }

    /**
     * The screen's pool, in damage. Whole numbers: a player is judging whether
     * a rush survives the doorway, not auditing a decimal.
     */
    private static String damage(float value) {
        return Math.round(value) + " damage";
    }

    /** Whole degrees with the sign; an arc is authored to the degree and read at a glance. */
    private static String degrees(float value) {
        return Math.round(value) + "°";
    }

    private static String availability(IntegralSystemDef system) {
        String active = "Runs for " + seconds(system.durationSeconds()) + " seconds";
        return system.usesAmmunition()
                ? active + ", and the suit carries " + uses(system.startingAmmo()) + "."
                : active + ", then " + seconds(system.cooldownSeconds())
                        + " before it can be spent again.";
    }

    /** The compact availability clock: {@code 3.0s / 22s}, or {@code 2 uses}. */
    private static String clock(IntegralSystemDef system) {
        return system.usesAmmunition()
                ? uses(system.startingAmmo())
                : seconds(system.durationSeconds()) + "s / " + seconds(system.cooldownSeconds()) + "s";
    }

    private static String uses(int ammo) {
        return ammo + (ammo == 1 ? " use" : " uses");
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
