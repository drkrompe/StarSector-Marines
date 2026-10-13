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
 */
public record GroundDefences(int batteries, boolean shieldGenerator, int nests, Map<FortKind, Integer> forts) {

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
        return new GroundDefences(batteries, shieldGenerator, nests, next);
    }

    /** True when the declaration places nothing at all. */
    public boolean isNone() {
        return batteries == 0 && !shieldGenerator && nests == 0 && forts.isEmpty();
    }
}
