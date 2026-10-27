package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.List;

/**
 * Which way a hull keeps its front ({@code convoy-nouns.md}, what presents the front): toward the known hostile whose
 * bearing, taken as the facing, has every hostile it knows of killing it slowest, each through the plate it would hit.
 * Alone, a threat gets the front. Against several from several sides the front goes where the plates they would meet
 * hold longest, so two guns on one side get the front and a lone one on the other the tail, and a side its attackers
 * have stripped is turned away from them.
 *
 * <p>Harm is expected damage per second into this hull's plate from where the threat stands now: each weapon's
 * rounds a second, at its accuracy for the range, at its armor efficiency against the plate
 * ({@link DurabilityModel#armorEfficiency}), with a contact payload read where it does more. A weapon out of its own
 * reach does none. On an arc the rating is the hull's times that arc's rating multiplier ({@link ArmorArcs}). A threat
 * kills through a plate in the time it takes to strip it (its armor left times its arc's plate multiplier, at the
 * harm against that plate) and then the structure behind (at the harm against no plate, since a stripped plate lets
 * every point through). A facing's cost is the sum over the threats of one over that time: a machine gun that barely
 * marks the glacis is slow death through it and quick death through a bare flank.
 */
final class ThreatFront {

    /**
     * {@code false} is the control: a hull keeps its front on the machine it duels, the nearest it sees, and a hull
     * with none does not turn.
     */
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
     * How much less harm another facing must take before a hull leaves the one it holds for it. A turn costs seconds
     * of a shaken gun and sweeps the side across the first threat, so two facings of about the same weight do not
     * trade the front back and forth.
     */
    static final float SWITCH_MARGIN = 1.25f;

    /**
     * One known hostile: where it stands from the hull ({@code dx}, {@code dy}), its harm against each arc's plate,
     * and its harm against no plate at all.
     */
    record Threat(long id, float dx, float dy, float front, float side, float rear, float bare) {
        float harm(ArmorArcs.Arc arc) {
            return switch (arc) {
                case FRONT -> front;
                case SIDE -> side;
                case REAR -> rear;
            };
        }
    }

    /**
     * What the hull holds on each plate (armor left times its arc's plate multiplier) and in the structure behind
     * them all.
     */
    record Hold(float front, float left, float right, float rear, float structure) {
        /** The plate a hit from {@code (dx, dy)} on {@code arc} meets, for a hull facing {@code facingDegrees}. */
        float plate(ArmorArcs.Arc arc, float facingDegrees, float dx, float dy) {
            return switch (arc) {
                case FRONT -> front;
                case REAR -> rear;
                case SIDE -> {
                    double radians = Math.toRadians(facingDegrees);
                    yield dx * Math.cos(radians) + dy * Math.sin(radians) > 0 ? right : left;
                }
            };
        }
    }

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

    /** {@code weapons} as a threat {@code (dx, dy)} away, rated against each arc of a hull with these plates. */
    static Threat threat(long id, float dx, float dy, List<WeaponDef> weapons, float armorRating, ArmorArcs.Profile arcs) {
        float distance = (float) Math.hypot(dx, dy);
        return new Threat(id, dx, dy,
                harm(weapons, distance, armorRating * arcs.frontRating()),
                harm(weapons, distance, armorRating * arcs.sideRating()),
                harm(weapons, distance, armorRating * arcs.rearRating()),
                harm(weapons, distance, 0f));
    }

    /**
     * How fast every threat kills a hull holding {@code hold} and facing {@code facingDegrees}: the sum over them of
     * one over the time each takes to strip the plate it would hit and then the structure behind it.
     */
    static float exposure(List<Threat> threats, Hold hold, float facingDegrees) {
        float sum = 0f;
        for (Threat t : threats) {
            ArmorArcs.Arc arc = ArmorArcs.classify(facingDegrees, t.dx(), t.dy());
            float onPlate = t.harm(arc);
            if (onPlate <= 0f || t.bare() <= 0f) continue;
            float seconds = hold.plate(arc, facingDegrees, t.dx(), t.dy()) / onPlate + hold.structure() / t.bare();
            sum += 1f / Math.max(1e-3f, seconds);
        }
        return sum;
    }

    /**
     * The threat to turn the front onto: of the bearings to the threats that can do any harm, the one with the least
     * {@link #exposure}, unless facing the one already faced ({@code current}) is within {@link #SWITCH_MARGIN} of it.
     * Zero when nothing known can do any harm from where it stands.
     */
    static long choose(List<Threat> threats, Hold hold, long current) {
        Threat best = null;
        float bestExposure = Float.MAX_VALUE;
        float currentExposure = Float.NaN;
        for (Threat t : threats) {
            if (t.bare() <= 0f) continue;
            float exposure = exposure(threats, hold, bearing(t));
            if (exposure < bestExposure) {
                best = t;
                bestExposure = exposure;
            }
            if (t.id() == current) currentExposure = exposure;
        }
        if (best == null) return 0L;
        if (currentExposure <= bestExposure * SWITCH_MARGIN) return current;
        return best.id();
    }

    /** The facing that puts {@code t} dead ahead. */
    static float bearing(Threat t) {
        return (float) Math.toDegrees(Math.atan2(-t.dx(), t.dy()));
    }

    /** Unarmored plate takes every point. */
    private static float efficiency(float penetration, float rating) {
        return rating > 0f ? DurabilityModel.armorEfficiency(penetration, rating) : 1f;
    }
}
