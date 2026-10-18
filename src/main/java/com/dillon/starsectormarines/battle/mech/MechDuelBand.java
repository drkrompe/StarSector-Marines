package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.List;

/**
 * Which distance an armored duel should be fought at: the mech's own installed
 * weapons read against the weapons of the machine it is fighting.
 *
 * <p>Three answers, in order of precedence:
 * <ul>
 *   <li><b>Stand off.</b> When the mech out-reaches its target by at least
 *       {@link #OUTRANGE_MARGIN} cells, it holds just outside the target's
 *       reach and inside its own — the only band in which it is hurt by
 *       nothing it is not also hurting back with.</li>
 *   <li><b>Close.</b> When one of its shorter bands puts out more pressure
 *       per point of pressure received than its longest band does, by the
 *       role's {@link #closeBias}, it closes to that band. A Brawler's chaingun
 *       and SRM against a long-range target is the case; Long-Range Support
 *       never closes.</li>
 *   <li><b>Duel.</b> Otherwise it fights from the outer part of its own
 *       longest band rather than walking inside it for no reason. Two equal
 *       machines meet here, not at contact.</li>
 * </ul>
 *
 * <p>Pressure is expected damage per second at a distance: damage times
 * rounds per cycle over the cooldown, times the weapon's range-falloff
 * accuracy there. It deliberately leaves out penetration against the target's
 * plate, heat, and ammunition depth: the choice is about which band trades
 * better, and those scale both sides of a trade alike far more often than
 * they reverse it.
 *
 * <p>Pure: no simulation, no state. {@link MechDuelRange} reads a live mech
 * and its target into {@link Weapon}s and executes the answer.
 */
public final class MechDuelBand {

    /** Cells a mech must out-reach its target by before it stands off outside that reach. */
    static final float OUTRANGE_MARGIN = 4f;
    /** How far outside the target's reach a stand-off holds, in cells. */
    static final float STANDOFF_CLEARANCE = 1.5f;
    /** How far inside its own reach a stand-off holds, in cells, so the guns still bear. */
    static final float REACH_CLEARANCE = 1f;
    /** A band is sampled at this fraction of a weapon's range: inside it, with a step to spare. */
    static final float SAMPLE_FRACTION = 0.9f;
    /** Duel band as fractions of the longest reach. */
    static final float DUEL_INNER = 0.72f;
    static final float DUEL_OUTER = 0.92f;
    /** A close band reaches in to this fraction of its sampled distance. */
    static final float CLOSE_INNER = 0.6f;
    /** Nothing is fought from closer than this, whatever the band. */
    static final float MIN_BAND = 4f;

    private MechDuelBand() {}

    public enum Stance { STANDOFF, CLOSE, DUEL }

    /**
     * One installed weapon as the band choice reads it: the distances it can
     * fire across, and its pressure before accuracy.
     */
    public record Weapon(float minRange, float range, float pressure,
                         float accuracy, float accuracyFalloff) {

        public static Weapon of(WeaponDef def) {
            float rounds = Math.max(1, def.burstCount) * Math.max(1, def.projectilesPerShot);
            float pressure = def.damage * rounds / Math.max(0.1f, def.cooldown);
            return new Weapon(def.minRange, def.range, pressure, def.accuracy, def.accuracyFalloff);
        }

        float pressureAt(float distance) {
            if (distance < minRange || distance > range) return 0f;
            return pressure * RangeFalloff.accuracy(accuracy, accuracyFalloff, distance, range);
        }
    }

    /** The band to fight from: hold while the target is between {@code min} and {@code max} cells away. */
    public record Band(Stance stance, float min, float max) {
        public boolean contains(float distance, float slack) {
            return distance >= min - slack && distance <= max + slack;
        }

        public float middle() {
            return (min + max) * 0.5f;
        }
    }

    /** Expected damage per second from {@code weapons} at {@code distance}. */
    static float pressureAt(List<Weapon> weapons, float distance) {
        float sum = 0f;
        for (Weapon w : weapons) sum += w.pressureAt(distance);
        return sum;
    }

    static float reach(List<Weapon> weapons) {
        float reach = 0f;
        for (Weapon w : weapons) reach = Math.max(reach, w.range);
        return reach;
    }

    /**
     * How much better a shorter band's trade must be than the longest band's
     * before this role closes to it. A Brawler closes on a small edge; the
     * others want a clear one; Long-Range Support never closes. Nobody closes
     * on a tie: between equal machines a shorter band gains nothing.
     */
    static float closeBias(MechRole role) {
        return switch (role) {
            case ASSAULT -> 1.05f;
            case BALANCED, ARMORED_SUPPORT -> 1.15f;
            case LR_SUPPORT -> Float.POSITIVE_INFINITY;
        };
    }

    /**
     * The band {@code own} should fight {@code threat} from, or null when the
     * mech has nothing that fires. An unarmed target is fought from the duel
     * band: nothing is gained by walking up to it.
     */
    public static Band choose(List<Weapon> own, List<Weapon> threat, MechRole role) {
        float reach = reach(own);
        if (reach <= 0f) return null;
        float threatReach = reach(threat);
        if (threatReach > 0f && reach - threatReach >= OUTRANGE_MARGIN) {
            return new Band(Stance.STANDOFF, threatReach + STANDOFF_CLEARANCE, reach - REACH_CLEARANCE);
        }
        Band duel = new Band(Stance.DUEL, Math.max(MIN_BAND, reach * DUEL_INNER), reach * DUEL_OUTER);
        if (threatReach <= 0f) return duel;

        float longSample = reach * SAMPLE_FRACTION;
        float longTrade = trade(own, threat, longSample);
        float bias = closeBias(role);
        float bestSample = -1f;
        float bestTrade = longTrade * bias;
        for (Weapon w : own) {
            float sample = w.range * SAMPLE_FRACTION;
            if (sample >= longSample - 0.5f || sample < MIN_BAND) continue;
            float trade = trade(own, threat, sample);
            if (trade >= bestTrade && (bestSample < 0f || trade > bestTrade || sample > bestSample)) {
                bestTrade = trade;
                bestSample = sample;
            }
        }
        if (bestSample < 0f) return duel;
        return new Band(Stance.CLOSE, Math.max(MIN_BAND, bestSample * CLOSE_INNER), bestSample);
    }

    /** Pressure given per pressure received at {@code distance}. */
    static float trade(List<Weapon> own, List<Weapon> threat, float distance) {
        return pressureAt(own, distance) / Math.max(1e-3f, pressureAt(threat, distance));
    }
}
