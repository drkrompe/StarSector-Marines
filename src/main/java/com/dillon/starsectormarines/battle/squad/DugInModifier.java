package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.CombatService;

import java.util.EnumSet;
import java.util.Set;

/**
 * What being dug in on a fighting position pays ({@link DigInSystem}): a braced rifle's extra
 * reach, and composure under fire. Never protection: cover already reduces damage and stops
 * rounds, and the cells of a position already carry it.
 */
public final class DugInModifier implements UnitModifier {

    private static final Set<UnitStat> STATS = EnumSet.of(UnitStat.ATTACK_RANGE, UnitStat.MORALE_DRAIN);

    private final CombatService combat;

    public DugInModifier(CombatService combat) {
        this.combat = combat;
    }

    @Override public String name(long unit) { return "unitModifierDugIn"; }

    @Override public Set<UnitStat> stats() { return STATS; }

    @Override
    public boolean appliesTo(long unit) {
        return combat.isDugIn(unit);
    }

    @Override
    public float flat(long unit, UnitStat stat) {
        return stat == UnitStat.ATTACK_RANGE ? DigInSystem.DUG_IN_RANGE_BONUS : 0f;
    }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        return stat == UnitStat.MORALE_DRAIN ? DigInSystem.DUG_IN_MORALE_DRAIN_MULT : 1f;
    }
}
