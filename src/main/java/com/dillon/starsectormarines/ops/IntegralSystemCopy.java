package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MissilePodSpec;

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
 * declares.</b> The two are not the same yet: a breacher assist authors a
 * frontal screen alongside its movement boost, and only the movement half runs
 * today. Advertising the screen before the mitigation lands would make the
 * Armory a brochure rather than evidence, so {@link #effects} lists the live
 * effects and gains the screen when that half ships.
 */
final class IntegralSystemCopy {

    private static final String SEPARATOR = "  ·  ";

    private IntegralSystemCopy() { }

    static boolean carried(MarineArmorCatalogDef armor) {
        return armor != null && armor.hasIntegralSystem();
    }

    /**
     * One line for a card or a cycling button: what it is and how often it is
     * available. Sized to sit where a carried special item's name sits, because
     * that symmetry is the point — a suit's system and a billet's special item
     * are separate issues, and the screen should not imply one costs the other.
     */
    static String summary(MarineArmorCatalogDef armor) {
        if (!carried(armor)) return "No integral system";
        IntegralSystemDef system = armor.integralSystem();
        return system.displayName() + SEPARATOR + clock(system);
    }

    /**
     * One compact line for a tile that has room for the mechanics but not the
     * prose: what it is, how often, and what it does. The authored description
     * is deliberately left out — it runs to a couple of hundred characters and
     * would clip rather than inform. That belongs in {@link #detail}.
     */
    static String tile(MarineArmorCatalogDef armor) {
        if (!carried(armor)) return summary(armor);
        String effect = effect(armor.integralSystem());
        return effect.isEmpty() ? summary(armor)
                : summary(armor) + SEPARATOR + effect + ".";
    }

    /** The hover copy: the authored description, then what it does and its clock. */
    static String detail(MarineArmorCatalogDef armor) {
        if (!carried(armor)) {
            return "This pattern carries no integral system. It fights on its plate, its"
                    + " balance, and whatever the billet is carrying.";
        }
        IntegralSystemDef system = armor.integralSystem();
        List<String> parts = new ArrayList<>();
        parts.add(system.description());
        String effect = effect(system);
        if (!effect.isEmpty()) {
            // Only a reverting effect (a temporary stat boost) is honestly
            // described as "the suit is exactly the suit it was" afterward —
            // a spent salvo doesn't revert anything, it's just gone.
            parts.add(system.breacherAssist() != null
                    ? effect + ", then the suit is exactly the suit it was."
                    : effect + ".");
        }
        parts.add(availability(system));
        return String.join("  //  ", parts);
    }

    /**
     * What the suit measurably does while the system runs. Only effects the
     * simulation actually applies appear here — see the class note.
     */
    private static String effect(IntegralSystemDef system) {
        BreacherAssistSpec breacher = system.breacherAssist();
        if (breacher != null) {
            return "Moves " + percent(breacher.moveSpeedMult() - 1f) + " faster while it runs";
        }
        MissilePodSpec pod = system.missilePod();
        if (pod != null) {
            WeaponDef weapon = pod.weaponDef();
            int salvo = Math.max(1, weapon.projectilesPerShot());
            return "Fires a salvo of " + salvo + (salvo == 1 ? " missile" : " missiles")
                    + " at a self-picked target";
        }
        return "";
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
