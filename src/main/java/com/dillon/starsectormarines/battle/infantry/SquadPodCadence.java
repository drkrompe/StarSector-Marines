package com.dillon.starsectormarines.battle.infantry;

/** Squad-owned launch spacing and fair handoff among presently eligible carriers. */
public final class SquadPodCadence {
    private int nextAllowedTick;
    private long lastShooter;

    public boolean ready(int tick) { return tick >= nextAllowedTick; }

    /** Next stable identity after the last successful carrier, wrapping as needed. */
    public boolean prefers(long candidate, long incumbent) {
        if (incumbent == 0L) return true;
        boolean candidateAfter = candidate > lastShooter;
        boolean incumbentAfter = incumbent > lastShooter;
        return candidateAfter != incumbentAfter ? candidateAfter : candidate < incumbent;
    }

    /** Refusal leaves the clock and handoff unchanged. A commit excludes this tick. */
    public void committed(long shooter, int tick, float intervalSeconds, float tickSeconds) {
        lastShooter = shooter;
        nextAllowedTick = tick + Math.max(1, (int) Math.ceil(intervalSeconds / tickSeconds));
    }
}
