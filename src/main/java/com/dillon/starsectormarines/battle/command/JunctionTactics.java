package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import java.util.List;

/**
 * Small territorial lesson: advance by place and hand gains to the militia. Once the company is fighting
 * for a second site, the first blow (the reserve, with the bonded warehouse's garrison) strikes the first gain;
 * once it holds the second, the second blow (a column off the Rill road with technicals behind it) strikes that,
 * while the company is committed further on.
 *
 * <p>Pressure never goes quiet: a blow whose trigger has not come {@link #QUIET_TICKS} after the last one (or
 * after the first gain) launches anyway, so a company that sits on its gains is hit all the same.
 */
public final class JunctionTactics {
    /** The longest the raiders wait between the first gain and the first blow, or between the two blows. */
    public static final int QUIET_TICKS = 45 * 30;
    private int firstSecured = -1, secondSecured = -1, roadTarget = -1;
    private int firstSecuredTick = -1, reserveTick = -1;
    private boolean pushedOn;

    /** Observes without a clock: only the company's gains launch the blows. */
    public void observe(List<CompoundState> states) { observe(states, -1); }

    /** @param tick the sim tick, or -1 for no clock */
    public void observe(List<CompoundState> states, int tick) {
        if (states.size() != 4) throw new IllegalArgumentException("Junction requires four sites");
        if (firstSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (states.get(i) == CompoundState.MARINE_HELD) { firstSecured = i; firstSecuredTick = tick; break; }
        }
        if (firstSecured >= 0 && secondSecured < 0) for (int i = 1; i < states.size(); i++) {
            if (i != firstSecured && states.get(i) == CompoundState.MARINE_HELD) { secondSecured = i; break; }
        }
        if (firstSecured >= 0) for (int i = 1; i < states.size(); i++)
            if (i != firstSecured && states.get(i) != CompoundState.DEFENDER_HELD) pushedOn = true;
        if (!pushedOn && quiet(firstSecuredTick, tick)) pushedOn = true;
        if (pushedOn && reserveTick < 0) reserveTick = tick;
        if (roadTarget < 0 && (secondSecured >= 0 || quiet(reserveTick, tick)))
            roadTarget = secondSecured >= 0 ? secondSecured : contested(states);
    }
    private static boolean quiet(int since, int tick) { return since >= 0 && tick >= 0 && tick - since >= QUIET_TICKS; }
    /** The site the company is fighting for beyond its first gain, else its first gain. */
    private int contested(List<CompoundState> states) {
        for (int i = 1; i < states.size(); i++)
            if (i != firstSecured && states.get(i) != CompoundState.DEFENDER_HELD) return i;
        return firstSecured;
    }
    public int firstSecured() { return firstSecured; }
    /** The second site the company secured, or -1. */
    public int secondSecured() { return secondSecured; }
    /** The site the second blow strikes, fixed when it launches: the second gain, or the contested site if the clock sent it; -1 before. */
    public int roadTarget() { return roadTarget; }
    public boolean roadColumnCommitted() { return roadTarget >= 0; }
    public int assaultTarget(List<CompoundState> states) {
        for (int i = 0; i < states.size(); i++) if (states.get(i) != CompoundState.MARINE_HELD) return i;
        return 3;
    }
    public int militiaTarget() { return Math.max(0, firstSecured); }
    /**
     * The reserve goes in once the company is contesting a second site. Its way to the first gain runs past
     * the western landing ground: sent at the first gain, it met the company's later boatloads coming ashore
     * there and died without reaching the militia; sent at the second gain, it arrived after the company had
     * already taken Dispatch. A contested second site means the boats are in and the company has moved on.
     */
    public boolean reserveCommitted() { return pushedOn; }
}
