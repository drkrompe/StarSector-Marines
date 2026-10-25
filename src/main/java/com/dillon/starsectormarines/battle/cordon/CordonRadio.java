package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * What the allies tell the company over the radio about one redoubt ({@code cordon-allied-command.md}, The radio):
 * the one place the company's knowledge of the line is decided, so fair knowledge is tested where it is made. The
 * old single "calling" is split by its causes, and the allies say what they are already doing about it.
 *
 * @param breached   a swarm body is inside the wall
 * @param unsettled  breached, or quiet for less than the settle window since the last body was inside
 * @param bled       half the garrison is down
 * @param uncovered  its sector had armour or mechs and has none left
 * @param helpComing a loaded carrier or a column is bound for it
 */
public record CordonRadio(int number, int x, int y, boolean manned, boolean breached, boolean unsettled,
                          boolean bled, boolean uncovered, boolean helpComing) {

    /** Seconds a breached redoubt must be quiet before its relief may go back. */
    public static final int SETTLE_SECONDS = 20;
    static final int SETTLE_TICKS = Math.round(SETTLE_SECONDS / BattleSimulation.TICK_DT);

    /**
     * The report for one redoubt.
     *
     * @param quietTicks ticks since a swarm body was last inside the wall ({@link Integer#MAX_VALUE} for never)
     */
    public static CordonRadio report(int number, int x, int y, boolean manned, int swarmInside, int standing,
                                     int garrison, int quietTicks, boolean uncovered, boolean helpComing) {
        if (!manned) return new CordonRadio(number, x, y, false, false, false, false, false, false);
        boolean breached = swarmInside > 0;
        return new CordonRadio(number, x, y, true, breached, breached || quietTicks < SETTLE_TICKS,
                standing * 2 <= garrison, uncovered, helpComing);
    }

    /** Whether the company has anything to do here: a breach not yet settled, or a bleed nobody is answering. */
    public boolean wantsRelief() {
        return manned && (unsettled || bled && !helpComing);
    }
}
