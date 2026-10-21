package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;

/**
 * Where a cargo marine's innate aptitude lands when they take a billet. A recruit's
 * roll is fixed by their service number; the intake sets the cut points that roll
 * is read against, out of a hundred. {@link #NEUTRAL} is every company's ordinary
 * intake; the founding hull's lounge raises it (`second-wind-refit-tree.md`): a
 * ship with somewhere to be off watch keeps the marginal hands and draws better ones.
 */
public record RecruitIntake(int exceptionalBelow, int giftedBelow, int steadyBelow) {
    public static final RecruitIntake NEUTRAL = new RecruitIntake(5, 25, 90);

    public RecruitIntake {
        if (exceptionalBelow < 0 || giftedBelow < exceptionalBelow || steadyBelow < giftedBelow || steadyBelow > 100)
            throw new IllegalArgumentException("Intake cut points must rise within a hundred");
    }

    /** The aptitude a roll in {@code [0, 100)} earns. */
    public SoldierAptitude aptitude(int roll) {
        if (roll < exceptionalBelow) return SoldierAptitude.EXCEPTIONAL;
        if (roll < giftedBelow) return SoldierAptitude.GIFTED;
        if (roll < steadyBelow) return SoldierAptitude.STEADY;
        return SoldierAptitude.LIMITED;
    }
}
