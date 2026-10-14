package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.fort.FortKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The ground defences a battle carries, as its mission declared them: how many fortress batteries, whether a shield
 * generator, and how many defence nests. The mission is the only authority for this. Map generation stages and battle
 * setup place exactly what the declaration says and never consult the market or the map's size to decide it; the
 * market's industries reach a mission only as signals on {@link TargetProfile}, which a mission definition may read
 * when it makes its declaration.
 *
 * <p>{@link #NONE} is the default for a mission that says nothing.
 *
 * @param batteries        fortress batteries to stand on the map; never negative
 * @param shieldGenerator  whether the defending garrison holds a shield generator beside its keep
 * @param nests            defence nests (small hardened anti-air and anti-infantry emplacements) to scatter over the
 *                         map; never negative
 * @param forts            walled forts to stand on the map, by kind; absent or zero is none. A battery fort is a
 *                         battery plus its walls, fire-control bunker, magazine and power building; a shield fort is
 *                         a shield generator plus its walls, inner keep, power plant and control room (see
 *                         {@code forts.md}). A fort is declared separately from a bare {@link #batteries} or
 *                         {@link #shieldGenerator} so scenes and debug can still stand a structure on its own
 * @param siting           where the declared forts stand relative to the battle; see {@link FortSitingPolicy}
 * @param airDefended     how many of the declared forts of each kind carry the optional anti-air aspect (a sandbagged
 *                        yard with a plotting console, a guided battery and flak positions; see {@code forts.md}); the
 *                        first that many forts of the kind placed, never more than the kind's declared count. Absent or
 *                        zero is none, so a legacy declaration reads as a fort with no anti-air
 */
public record GroundDefences(int batteries, boolean shieldGenerator, int nests, Map<FortKind, Integer> forts,
                             FortSitingPolicy siting, Map<FortKind, Integer> airDefended) {

    public static final GroundDefences NONE = new GroundDefences(0, false, 0);

    public GroundDefences {
        if (batteries < 0) batteries = 0;
        if (nests < 0) nests = 0;
        EnumMap<FortKind, Integer> kept = new EnumMap<>(FortKind.class);
        if (forts != null) {
            for (Map.Entry<FortKind, Integer> e : forts.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) kept.put(e.getKey(), e.getValue());
            }
        }
        forts = Collections.unmodifiableMap(kept);
        if (siting == null) siting = FortSitingPolicy.LEFTOVER_GROUND;
        EnumMap<FortKind, Integer> air = new EnumMap<>(FortKind.class);
        if (airDefended != null) {
            for (Map.Entry<FortKind, Integer> e : airDefended.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) air.put(e.getKey(), e.getValue());
            }
        }
        airDefended = Collections.unmodifiableMap(air);
    }

    /** A declaration with these forts, this siting and no anti-air. */
    public GroundDefences(int batteries, boolean shieldGenerator, int nests, Map<FortKind, Integer> forts,
                          FortSitingPolicy siting) {
        this(batteries, shieldGenerator, nests, forts, siting, Map.of());
    }

    /** A declaration with these forts and the default (leftover-ground) siting. */
    public GroundDefences(int batteries, boolean shieldGenerator, int nests, Map<FortKind, Integer> forts) {
        this(batteries, shieldGenerator, nests, forts, FortSitingPolicy.LEFTOVER_GROUND);
    }

    /** A declaration with no forts. */
    public GroundDefences(int batteries, boolean shieldGenerator, int nests) {
        this(batteries, shieldGenerator, nests, Map.of());
    }

    /** A declaration with no nests, for callers that speak only of the large structures. */
    public GroundDefences(int batteries, boolean shieldGenerator) {
        this(batteries, shieldGenerator, 0);
    }

    /** How many forts of {@code kind} are declared. */
    public int fortCount(FortKind kind) {
        return forts.getOrDefault(kind, 0);
    }

    /** This declaration with {@code count} forts of {@code kind} (zero removes them). */
    public GroundDefences withForts(FortKind kind, int count) {
        EnumMap<FortKind, Integer> next = new EnumMap<>(FortKind.class);
        next.putAll(forts);
        next.put(kind, count);
        return new GroundDefences(batteries, shieldGenerator, nests, next, siting, airDefended);
    }

    /** This declaration with its forts sited by {@code policy}; a mission states how its forts are placed. */
    public GroundDefences withSiting(FortSitingPolicy policy) {
        return new GroundDefences(batteries, shieldGenerator, nests, forts, policy, airDefended);
    }

    /**
     * How many forts of {@code kind} carry the anti-air aspect: the first that many placed, at most the declared count.
     */
    public int airDefendedCount(FortKind kind) {
        return Math.min(fortCount(kind), airDefended.getOrDefault(kind, 0));
    }

    /** This declaration with these forts (by kind, as a count each) carrying the anti-air aspect. */
    public GroundDefences withAirDefences(Map<FortKind, Integer> air) {
        return new GroundDefences(batteries, shieldGenerator, nests, forts, siting, air);
    }

    /** This declaration with the first {@code count} forts of {@code kind} carrying the anti-air aspect (zero removes it). */
    public GroundDefences withAirDefence(FortKind kind, int count) {
        EnumMap<FortKind, Integer> next = new EnumMap<>(FortKind.class);
        next.putAll(airDefended);
        next.put(kind, count);
        return new GroundDefences(batteries, shieldGenerator, nests, forts, siting, next);
    }

    /** True when the declaration places nothing at all. */
    public boolean isNone() {
        return batteries == 0 && !shieldGenerator && nests == 0 && forts.isEmpty();
    }
}
