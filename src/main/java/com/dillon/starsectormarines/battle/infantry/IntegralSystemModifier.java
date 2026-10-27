package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.marine.IntegralSystemDef;

import java.util.EnumSet;
import java.util.Set;

/**
 * A running suit system's stance ({@code integral-armor-systems.md}): a breacher assist speeds the
 * wearer. Applies only while the system is running, so its
 * expiry is the end of the effect with nothing to restore.
 */
public final class IntegralSystemModifier implements UnitModifier {

    private static final Set<UnitStat> STATS = EnumSet.of(UnitStat.MOVE_SPEED);

    private final IntegralSystemService systems;

    public IntegralSystemModifier(IntegralSystemService systems) {
        this.systems = systems;
    }

    @Override
    public String name(long unit) {
        IntegralSystemDef def = systems.spec(unit);
        return def != null ? def.displayName() : "";
    }

    @Override public Set<UnitStat> stats() { return STATS; }

    @Override
    public boolean appliesTo(long unit) {
        return systems.isActive(unit);
    }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        return switch (stat) {
            case MOVE_SPEED -> Math.max(0f, systems.moveSpeedMultiplier(unit));
            default -> 1f;
        };
    }
}
