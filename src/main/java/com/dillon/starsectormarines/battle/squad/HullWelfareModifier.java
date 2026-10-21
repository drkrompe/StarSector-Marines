package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.EnumSet;
import java.util.Set;

/**
 * The founding hull's training rooms on the marines of a campaign squad: the range's
 * drill on primary accuracy, the gym's conditioning on move speed and on morale lost
 * under fire. Read off the squad's launch-frozen {@link HullWelfare}, so it is one
 * named, company-wide figure the player can see on the briefing.
 */
public final class HullWelfareModifier implements UnitModifier {

    private static final Set<UnitStat> STATS = EnumSet.of(UnitStat.ACCURACY, UnitStat.MOVE_SPEED, UnitStat.MORALE_DRAIN);

    private final UnitRosterService roster;

    public HullWelfareModifier(UnitRosterService roster) {
        this.roster = roster;
    }

    @Override public String name(long unit) { return "unitModifierHullWelfare"; }

    @Override public Set<UnitStat> stats() { return STATS; }

    @Override
    public boolean appliesTo(long unit) {
        HullWelfare welfare = welfare(unit);
        return welfare != null && welfare.changesUnitStats();
    }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        HullWelfare welfare = welfare(unit);
        return welfare == null ? 1f : multiplier(welfare, stat);
    }

    static float multiplier(HullWelfare welfare, UnitStat stat) {
        return switch (stat) {
            case ACCURACY -> welfare.accuracy();
            case MOVE_SPEED -> welfare.moveSpeed();
            case MORALE_DRAIN -> welfare.moraleDrain();
            default -> 1f;
        };
    }

    private HullWelfare welfare(long unit) {
        if (!roster.squad().hasSquad(unit)) return null;
        Squad squad = roster.getSquad(roster.squad().squadId(unit));
        return squad != null ? squad.hullWelfare : null;
    }
}
