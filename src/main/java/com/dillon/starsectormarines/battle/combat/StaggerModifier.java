package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;

import java.util.EnumSet;
import java.util.Set;

/** A body that {@linkplain StaggerService can't act} does not move: move speed is held at zero. */
public final class StaggerModifier implements UnitModifier {

    private static final Set<UnitStat> STATS = EnumSet.of(UnitStat.MOVE_SPEED);

    private final StaggerService stagger;

    public StaggerModifier(StaggerService stagger) {
        this.stagger = stagger;
    }

    @Override public String name(long unit) { return stagger.cause(unit).nameKey; }

    @Override public Set<UnitStat> stats() { return STATS; }

    @Override public boolean appliesTo(long unit) { return stagger.isStaggered(unit); }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        return stat == UnitStat.MOVE_SPEED ? 0f : 1f;
    }
}
