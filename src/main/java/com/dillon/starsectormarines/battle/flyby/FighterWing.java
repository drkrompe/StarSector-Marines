package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.Faction;

/**
 * One commitment of fighter support to a battle — a single profile flying for
 * one faction, with a schedule of how many sorties arrive and when. The
 * {@link FlybyOverlay} drives spawns from these: tick a sim-time accumulator,
 * and when {@code simTime ≥ firstArrivalSec + spawnsSoFar·spawnIntervalSec},
 * spawn the next sortie until {@link #sortieCount} are exhausted.
 *
 * <p>"Sortie" here means one pass across the battle — the fighter spawns at
 * a map edge, weaves through, optionally commits to a strafing run, and exits
 * off the opposite side. A wing with {@code sortieCount = 3} represents
 * three such passes (modeling rearm + return-to-base between them via the
 * spawn interval).
 *
 * <p>Plain immutable data; lives on {@link com.dillon.starsectormarines.ops.Mission}
 * via {@link FlybyRoster} and gets read into the sim at battle-start.
 */
public final class FighterWing {

    public final FighterProfile profile;
    public final Faction side;
    public final int sortieCount;
    public final float firstArrivalSec;
    public final float spawnIntervalSec;

    public FighterWing(FighterProfile profile, Faction side,
                       int sortieCount, float firstArrivalSec, float spawnIntervalSec) {
        this.profile = profile;
        this.side = side;
        this.sortieCount = Math.max(1, sortieCount);
        this.firstArrivalSec = Math.max(0f, firstArrivalSec);
        this.spawnIntervalSec = Math.max(0.1f, spawnIntervalSec);
    }

    /** Convenience for the common "one sortie, arrives at T+N" case. */
    public static FighterWing single(FighterProfile profile, Faction side, float firstArrivalSec) {
        return new FighterWing(profile, side, 1, firstArrivalSec, 1f);
    }
}
