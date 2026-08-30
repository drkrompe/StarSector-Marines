package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One commitment of fighter support to a battle — a single profile flying for
 * one faction, with a schedule of how many sorties arrive and when.
 * {@code air.AirCoverSystem} drives dispatch from these: tick a sim-time
 * accumulator, and fly the wing's next sortie whenever one comes due, until
 * {@link #sortieCount} are exhausted.
 *
 * <p>"Sortie" here means one aircraft over the battle: in across a map edge on
 * an {@code air.AirCorridor}, gun runs on the densest enemy concentration, then
 * out across the same edge. A wing with {@code sortieCount = 3} is three such
 * trips, the interval standing for the rearm between them.
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
