package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One commitment of fighter support to a battle — a single profile flying for
 * one faction. {@code air.AirCoverSystem} drives dispatch from these.
 *
 * <p>"Sortie" here means one aircraft over the battle: in across a map edge on
 * an {@code air.AirCorridor}, gun runs on the densest enemy concentration, then
 * out across the same edge.
 *
 * <p>A wing is one of two kinds:
 * <ul>
 *   <li><b>Scheduled</b> — a fixed stock of sorties: sortie {@code k} is due at
 *       {@code firstArrivalSec + k * spawnIntervalSec}, until
 *       {@link #sortieCount} are flown. A mission-generated employer or enemy
 *       wing is one of these.</li>
 *   <li><b>A bay</b> ({@link #bay}) — one airframe kept on station for the
 *       whole battle, the way a carrier's fighters work in space combat. It
 *       flies, comes home and is back up after {@link #refitSec}; shot down,
 *       the bay replaces it after {@link #replacementSec}. The stock never runs
 *       out. A player's committed carrier bay is one of these.</li>
 * </ul>
 *
 * <p>Plain immutable data; lives on {@link com.dillon.starsectormarines.ops.Mission}
 * via {@link FlybyRoster} and gets read into the sim at battle-start.
 */
public final class FighterWing {

    public final FighterProfile profile;
    public final Faction side;
    /** Sorties in a scheduled wing's stock; for a bay, its one airframe on station. */
    public final int sortieCount;
    public final float firstArrivalSec;
    /** Sim-seconds between a scheduled wing's sorties; unused by a bay. */
    public final float spawnIntervalSec;
    /** Sim-seconds a bay's airframe spends rearming after it comes home; 0 for a scheduled wing. */
    public final float refitSec;
    /** Sim-seconds a bay takes to replace an airframe that was shot down; 0 for a scheduled wing. */
    public final float replacementSec;

    public FighterWing(FighterProfile profile, Faction side,
                       int sortieCount, float firstArrivalSec, float spawnIntervalSec) {
        this(profile, side, sortieCount, firstArrivalSec, spawnIntervalSec, 0f, 0f);
    }

    private FighterWing(FighterProfile profile, Faction side, int sortieCount,
                        float firstArrivalSec, float spawnIntervalSec,
                        float refitSec, float replacementSec) {
        this.profile = profile;
        this.side = side;
        this.sortieCount = Math.max(1, sortieCount);
        this.firstArrivalSec = Math.max(0f, firstArrivalSec);
        this.spawnIntervalSec = Math.max(0.1f, spawnIntervalSec);
        this.refitSec = refitSec;
        this.replacementSec = replacementSec;
    }

    /** Convenience for the common "one sortie, arrives at T+N" case. */
    public static FighterWing single(FighterProfile profile, Faction side, float firstArrivalSec) {
        return new FighterWing(profile, side, 1, firstArrivalSec, 1f);
    }

    /**
     * A renewable bay: one airframe first up at {@code firstArrivalSec}, back
     * up {@code refitSec} after each sortie it comes home from, and replaced
     * {@code replacementSec} after each one it loses.
     */
    public static FighterWing bay(FighterProfile profile, Faction side, float firstArrivalSec,
                                  float refitSec, float replacementSec) {
        return new FighterWing(profile, side, 1, firstArrivalSec, 1f,
                Math.max(0.1f, refitSec), Math.max(0.1f, replacementSec));
    }

    /** Whether this is a renewable {@link #bay} rather than a scheduled stock of sorties. */
    public boolean renewable() {
        return refitSec > 0f;
    }
}
