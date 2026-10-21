package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;

import java.util.List;

/**
 * The rescue commander's rule for opting in to a bore strike
 * ({@code colony-breach-nouns.md}, Readiness gate), read from what it can see
 * at one command pulse:
 *
 * <ol>
 *   <li><b>the lift is hardened</b>: the company has built defences standing
 *       at the lift and has lost nobody on the lift line for a sustained
 *       window;</li>
 *   <li><b>the strength left behind meets the escort's need</b>: after the
 *       detachment leaves, there is still a squad for every walking group, a
 *       squad for every redoubt not yet relieved, and one more at the lift;</li>
 *   <li><b>a detachment exists</b>: some squad free of escort duty carries
 *       demolition, heavy weapons or is a machine.</li>
 * </ol>
 *
 * <p>Purge relaxes the first two against the mother bore. Evacuate never asks
 * about the lift, because the only bore it engages is one already on the walk
 * out; it still keeps the escort's strength.
 *
 * @param liftHardened        condition 1
 * @param strengthHolds       condition 2
 * @param detachmentAvailable condition 3
 * @param relaxed             whether Purge waived 1 and 2 for this target
 * @param open                the verdict
 */
public record ReadinessGate(boolean liftHardened, boolean strengthHolds, boolean detachmentAvailable,
                            boolean relaxed, boolean open) {

    /** Ticks without a loss on the lift line before built defences count as a hardened lift: twenty seconds. */
    public static final int HARDENED_WINDOW_TICKS = 600;
    /** Squads kept at the lift beyond the escorts and the relief, whatever else is owed. */
    public static final int LIFT_RESERVE_SQUADS = 1;

    /**
     * What the gate is read from.
     *
     * @param liftDefences   the company's living emplacements on the lift line
     * @param liftQuietTicks ticks since the later of a loss on the lift line and the defences standing there
     * @param leftBehind     squads of the mobile force that would stay with the cohort
     * @param escortNeed     squads the cohort needs ({@link #escortNeed})
     * @param capableSquads  squads that could form a detachment
     */
    public record Inputs(int liftDefences, int liftQuietTicks, int leftBehind, int escortNeed, int capableSquads) { }

    /** The gate for {@code posture} against one target. */
    public static ReadinessGate evaluate(HivePosture posture, boolean againstMother, Inputs in) {
        boolean hardened = in.liftDefences() > 0 && in.liftQuietTicks() >= HARDENED_WINDOW_TICKS;
        boolean strength = in.leftBehind() >= in.escortNeed();
        boolean detachment = in.capableSquads() > 0;
        boolean relaxed = posture == HivePosture.PURGE && againstMother;
        boolean open = switch (posture) {
            case EVACUATE -> strength && detachment;
            case OPPORTUNISTIC -> hardened && strength && detachment;
            case PURGE -> relaxed ? detachment : hardened && strength && detachment;
        };
        return new ReadinessGate(hardened, strength, detachment, relaxed, open);
    }

    /**
     * Squads the cohort needs kept with it: one escort per group walking out,
     * one for every redoubt still waiting to be relieved (a holdout left
     * pending is unmet need, not spare strength), and the lift reserve. A
     * rescue with no redoubt list is one cohort: an escort or a relief.
     */
    public static int escortNeed(List<CivilianRedoubt> redoubts) {
        if (redoubts.isEmpty()) return 1 + LIFT_RESERVE_SQUADS;
        int need = LIFT_RESERVE_SQUADS;
        for (CivilianRedoubt redoubt : redoubts) {
            if (redoubt.walking() > 0) need++;
            if (redoubt.pending()) need++;
        }
        return need;
    }

    /** One line for traces: {@code H S D} flags, upper case when met. */
    public String describe() {
        return (liftHardened ? "H" : "h") + (strengthHolds ? "S" : "s") + (detachmentAvailable ? "D" : "d")
                + (relaxed ? "+relaxed" : "") + (open ? " open" : " closed");
    }
}
