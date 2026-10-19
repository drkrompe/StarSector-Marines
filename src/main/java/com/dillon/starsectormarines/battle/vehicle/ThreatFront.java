package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.List;

/**
 * Which known hostile machine a hull keeps its front on ({@code convoy-nouns.md}, what presents the front): the one
 * that can do it the most harm, not merely the nearest. A hull has one front plate. Against two machines from two
 * sides the plate goes to the gun that would hurt most through the side, and the other gets the side.
 *
 * <p>Harm is expected damage per second into this hull's plate from where the threat stands now: each weapon's
 * rounds a second, at its accuracy for the range, at its armor efficiency against the plate
 * ({@link DurabilityModel#armorEfficiency}), with a contact payload read where it does more. A weapon out of its own
 * reach does none. So a heavy machine gun that cannot get through a tank's plate rates far below a cannon that can,
 * wherever the two stand.
 */
final class ThreatFront {

    /** {@code false} is the control: a hull keeps its front on the machine it duels, the nearest it sees. */
    static final String PROPERTY = "battle.vehicle.faceWorstThreat";

    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * {@code false} is the control: a hull knows every hostile machine, seen or not. By default it knows only what it
     * sees or its side's spotters report ({@link SideSpotting}), so a flanker behind cover gets its shots on the side
     * until it is spotted.
     */
    static final String KNOWN_ONLY_PROPERTY = "battle.vehicle.faceKnownOnly";

    static final boolean KNOWN_ONLY = Boolean.parseBoolean(System.getProperty(KNOWN_ONLY_PROPERTY, "true"));

    /**
     * How much worse another threat must be before a hull leaves the one it is facing for it. A turn costs seconds of
     * a shaken gun and sweeps the side across the first threat, so two threats of about the same weight do not
     * trade the front back and forth.
     */
    static final float SWITCH_MARGIN = 1.25f;

    /** One known hostile machine and the harm it can do this hull from where it stands. */
    record Threat(long id, float harm) {}

    private ThreatFront() {}

    /** Expected damage per second {@code weapons} put into plate of {@code armorRating} from {@code distance}. */
    static float harm(List<WeaponDef> weapons, float distance, float armorRating) {
        float sum = 0f;
        for (WeaponDef def : weapons) {
            if (def == null || distance < def.minRange || distance > def.range) continue;
            float perRound = Math.max(def.damage * efficiency(def.penetration, armorRating),
                    def.contactDamage * efficiency(def.contactPenetration, armorRating));
            float rounds = Math.max(1, def.burstCount) * Math.max(1, def.projectilesPerShot);
            sum += perRound * rounds / Math.max(0.1f, def.cooldown)
                    * RangeFalloff.accuracy(def.accuracy, def.accuracyFalloff, distance, def.range);
        }
        return sum;
    }

    /**
     * The threat to face: the most harmful, unless the one already faced ({@code current}) is within
     * {@link #SWITCH_MARGIN} of it. Zero when nothing known can do any harm from where it stands.
     */
    static long choose(List<Threat> threats, long current) {
        Threat worst = null;
        float currentHarm = 0f;
        for (Threat t : threats) {
            if (t.harm() <= 0f) continue;
            if (worst == null || t.harm() > worst.harm()) worst = t;
            if (t.id() == current) currentHarm = t.harm();
        }
        if (worst == null) return 0L;
        if (currentHarm > 0f && currentHarm * SWITCH_MARGIN >= worst.harm()) return current;
        return worst.id();
    }

    /** Unarmored plate takes every point. */
    private static float efficiency(float penetration, float rating) {
        return rating > 0f ? DurabilityModel.armorEfficiency(penetration, rating) : 1f;
    }
}
