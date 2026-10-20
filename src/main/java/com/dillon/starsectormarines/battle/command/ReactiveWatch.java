package com.dillon.starsectormarines.battle.command;

import java.util.List;

/**
 * One squad's reactive manning of the places around it: the squad-scope counterpart of {@link GarrisonDirector}, for a
 * squad that watches a patch of ground on its own rather than being sent by a commander. Its first caller is a
 * player's squad that has opted into the GARRISON stance (`garrisonables.md`); nothing here knows which side it serves.
 *
 * <p>Pure: the caller prices the places it found near the squad's anchor into {@link Candidate}s from its own side's
 * influence picture and believed contacts, and turns the answers into orders. The thresholds are the director's own
 * ({@link GarrisonDirector#START_PRESSURE}, {@link GarrisonDirector#RELEASE_PRESSURE},
 * {@link GarrisonDirector#DWELL_PULSES}, {@link GarrisonDirector#usefulFloor}), so a squad watching for itself and a
 * commander's patrol answer pressure by one law.
 *
 * <ul>
 *   <li><b>Start.</b> A place is manned when its believed pressure reaches the start level, most pressed first, ties
 *       to the nearer and then the lower key; only places inside the watch, not taken by another squad, not believed
 *       to hold an enemy, not locked out after a breach, and reachable in time.</li>
 *   <li><b>Useful strength.</b> A squad below useful strength is never sent, and one that falls below it on its place
 *       falls back.</li>
 *   <li><b>Hysteresis.</b> A manned place is given back only after its pressure has stayed below the lower release
 *       level for the dwell, and never while the squad is in a fight. Between the two levels nothing changes.</li>
 *   <li><b>Breach.</b> A believed enemy inside the place near the squad (or anywhere in it before the squad has
 *       arrived) is a fall back.</li>
 * </ul>
 */
public final class ReactiveWatch {

    /** How far from its anchor a watching squad looks for places: the defend-area ring the player already knows. */
    public static final int WATCH_RADIUS_CELLS = 20;
    /** One decision pulse, in ticks: the command cadence the dwell is counted in (2.5 s at 30 Hz). */
    public static final int PULSE_TICKS = 75;
    /** Believed pressure at which a place is manned: the director's start level. */
    public static final float START_PRESSURE = GarrisonDirector.START_PRESSURE;
    /** Believed pressure below which a manned place counts as cool: the director's release level. */
    public static final float RELEASE_PRESSURE = GarrisonDirector.RELEASE_PRESSURE;
    /** Consecutive cool pulses before a manned place is given back: the director's dwell. */
    public static final int DWELL_PULSES = GarrisonDirector.DWELL_PULSES;
    /** Pulses a squad leaves a place it was breached out of before it may man it again. */
    public static final int LOCKOUT_PULSES = GarrisonDirector.BREACH_LOCKOUT_PULSES;
    /** A believed contact lighter than this (confidence times strength) is not counted as inside a place. */
    public static final float MIN_CONTACT_WEIGHT = GarrisonDirector.MIN_CONTACT_WEIGHT;

    private ReactiveWatch() { }

    /**
     * A place a watching squad could man, priced for this pulse.
     *
     * @param key         the caller's stable identity for the place
     * @param gapCells    Chebyshev cells from the squad's anchor to the place's nearest cell (0 inside it)
     * @param etaTicks    the squad's march to it ({@link #etaTicks})
     * @param pressure    the believed hostile pressure on it: the highest of its covered influence blocks
     * @param taken       whether another squad of the side mans it or has been ordered onto it
     * @param enemyInside whether a believed enemy stands inside it
     * @param lockedOut   whether this squad was breached out of it recently
     */
    public record Candidate(long key, int gapCells, int etaTicks, float pressure, boolean taken,
                            boolean enemyInside, boolean lockedOut) { }

    /** What a squad on its place does this pulse. */
    public enum Keep { STAY, RELEASE, FALL_BACK }

    /** Whether a squad raised at {@code originalMembers} with {@code aliveMembers} left is strong enough to man a place. */
    public static boolean useful(int aliveMembers, int originalMembers) {
        return aliveMembers > 0 && aliveMembers >= GarrisonDirector.usefulFloor(originalMembers, aliveMembers);
    }

    /** The march, in ticks, from a cell to a place's cell, priced as the commander prices one. */
    public static int etaTicks(int fromX, int fromY, int toX, int toY) {
        return GarrisonDirector.etaTicks(fromX, fromY, toX, toY);
    }

    /** Chebyshev cells from a cell to a rectangle; 0 inside it. */
    public static int gap(int x, int y, int left, int top, int right, int bottom) {
        int dx = x < left ? left - x : x > right ? x - right : 0;
        int dy = y < top ? top - y : y > bottom ? y - bottom : 0;
        return Math.max(dx, dy);
    }

    /**
     * The index in {@code candidates} of the place to man, or -1: none is pressed enough, the squad is not at useful
     * strength, or every pressed place is ruled out.
     */
    public static int choose(List<Candidate> candidates, int aliveMembers, int originalMembers) {
        if (!useful(aliveMembers, originalMembers)) return -1;
        int best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.pressure() < GarrisonDirector.START_PRESSURE || c.taken() || c.enemyInside() || c.lockedOut()
                    || c.gapCells() > WATCH_RADIUS_CELLS || c.etaTicks() > GarrisonDirector.MAX_ETA_TICKS) continue;
            if (best < 0 || better(c, candidates.get(best))) best = i;
        }
        return best;
    }

    private static boolean better(Candidate a, Candidate b) {
        if (a.pressure() != b.pressure()) return a.pressure() > b.pressure();
        if (a.gapCells() != b.gapCells()) return a.gapCells() < b.gapCells();
        return a.key() < b.key();
    }

    /** The consecutive cool pulses after this one: reset while the place is at or above the release level. */
    public static int cooled(int previous, float pressure) {
        return pressure >= GarrisonDirector.RELEASE_PRESSURE ? 0 : previous + 1;
    }

    /**
     * Whether a squad manning a place stays, is given back, or falls back.
     *
     * @param coolPulses consecutive cool pulses including this one ({@link #cooled})
     * @param inFight    whether the squad holds an actionable contact of its own
     * @param breached   whether a believed enemy is inside the place near it ({@link #breached})
     */
    public static Keep keep(int coolPulses, boolean inFight, int aliveMembers, int originalMembers, boolean breached) {
        if (breached || !useful(aliveMembers, originalMembers)) return Keep.FALL_BACK;
        if (coolPulses >= GarrisonDirector.DWELL_PULSES && !inFight) return Keep.RELEASE;
        return Keep.STAY;
    }

    /**
     * Whether a believed enemy at ({@code enemyX}, {@code enemyY}), already known to stand inside the squad's place,
     * breaches it: any such enemy before the squad has arrived, and once it has, one within the director's breach
     * range of the squad, since a report at the far end of a long run is somebody else's stretch.
     */
    public static boolean breached(boolean arrived, int squadX, int squadY, int enemyX, int enemyY) {
        return !arrived || Math.max(Math.abs(squadX - enemyX), Math.abs(squadY - enemyY))
                <= GarrisonDirector.BREACH_RANGE_CELLS;
    }

    /** Whether a squad has wandered far enough from its anchor that its watch should move with it. */
    public static boolean drifted(int anchorX, int anchorY, int squadX, int squadY) {
        return Math.max(Math.abs(anchorX - squadX), Math.abs(anchorY - squadY)) > WATCH_RADIUS_CELLS;
    }
}
