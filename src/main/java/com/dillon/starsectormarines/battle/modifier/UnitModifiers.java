package com.dillon.starsectormarines.battle.modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The modifiers in force in one battle, and the one place a modified stat is resolved:
 * {@code (base + sum of flats) * product of multipliers} over the modifiers that apply to the
 * unit. Read through at every use rather than written onto the unit, so overlapping modifiers
 * cannot compound each other's leftovers and an expiry has nothing to restore.
 *
 * <p>Modifiers are registered while the battle is being set up and only read afterwards, from
 * any thread. Each stat keeps its own list, so a hot read such as attack range asks only the
 * modifiers that can change range.
 */
public final class UnitModifiers {

    /** One modifier's part in a resolved stat, for presentation and evidence. */
    public record Applied(String name, float flat, float multiplier) { }

    private final UnitModifier[][] byStat = new UnitModifier[UnitStat.VALUES.length][];

    public UnitModifiers() {
        Arrays.fill(byStat, new UnitModifier[0]);
    }

    /** Adds a modifier for every stat it declares. Setup only. */
    public void register(UnitModifier modifier) {
        for (UnitStat stat : modifier.stats()) {
            UnitModifier[] current = byStat[stat.ordinal()];
            UnitModifier[] next = Arrays.copyOf(current, current.length + 1);
            next[current.length] = modifier;
            byStat[stat.ordinal()] = next;
        }
    }

    /** {@code base} as modified for {@code unit} right now. */
    public float resolve(long unit, UnitStat stat, float base) {
        UnitModifier[] modifiers = byStat[stat.ordinal()];
        if (modifiers.length == 0) return base;
        float flat = 0f;
        float multiplier = 1f;
        for (UnitModifier modifier : modifiers) {
            if (!modifier.appliesTo(unit)) continue;
            flat += modifier.flat(unit, stat);
            multiplier *= modifier.multiplier(unit, stat);
        }
        return (base + flat) * multiplier;
    }

    /** The modifiers changing {@code stat} for {@code unit} right now, in registration order. */
    public List<Applied> explain(long unit, UnitStat stat) {
        List<Applied> applied = new ArrayList<>();
        for (UnitModifier modifier : byStat[stat.ordinal()]) {
            if (!modifier.appliesTo(unit)) continue;
            applied.add(new Applied(modifier.name(unit), modifier.flat(unit, stat), modifier.multiplier(unit, stat)));
        }
        return applied;
    }
}
