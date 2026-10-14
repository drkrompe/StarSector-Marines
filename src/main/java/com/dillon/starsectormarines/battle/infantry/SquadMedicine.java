package com.dillon.starsectormarines.battle.infantry;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * What a squad's designated medic can do at one rank of the officer's Field
 * Medicine training ({@code squad-medic.md}).
 *
 * <p>The limits are supplies, their slow recovery, and a cooldown between
 * treatments. Restoration is flat per second rather than a fraction of the
 * patient's maximum, for the reason {@code FieldAidSpec} gives: a bandage is a
 * bandage, and it goes further on somebody with less to lose.
 *
 * @param supplies             treatments carried, and the cap recovery refills to
 * @param supplyRecoverySeconds seconds to recover one supply while below the cap
 * @param cooldownSeconds      seconds between the end of one treatment and the next
 * @param channelSeconds       length of one full treatment
 * @param healthPerSecond      structure restored per second of treatment
 * @param armorPerSecond       armor capacity restored per second of treatment
 * @param reachCells           how close the medic must be to the patient to treat
 * @param leashCells           how far from the medic a patient may be to be chosen
 * @param approachSeconds      how long the medic may spend reaching a patient
 * @param treatBelowHealth     a patient qualifies below this fraction of maximum health
 * @param treatBelowArmor      or below this fraction of armor capacity, broken included
 * @param heavyFirePressure    incoming pressure at which the medic will not set out
 */
public record SquadMedicine(int supplies, float supplyRecoverySeconds, float cooldownSeconds,
                            float channelSeconds, float healthPerSecond, float armorPerSecond,
                            float reachCells, float leashCells, float approachSeconds,
                            float treatBelowHealth, float treatBelowArmor,
                            float heavyFirePressure) {

    /** Highest Field Medicine rank an officer can train. */
    public static final int MAX_RANK = 2;

    private static final SquadMedicine RANK_ONE = new SquadMedicine(3, 90f, 10f, 5f,
            2.5f, 1f, 1.5f, InfantryCohesion.COHESION_RADIUS, 8f, 0.7f, 0.5f, 2f);
    private static final SquadMedicine RANK_TWO = new SquadMedicine(4, 60f, 8f, 5f,
            3f, 1.5f, 1.5f, InfantryCohesion.COHESION_RADIUS, 8f, 0.7f, 0.5f, 2f);

    /** The fire team a medic is drawn from first: the middle one, Bravo. */
    public static final int MEDIC_FIRE_TEAM = 1;

    /**
     * Which billet carries the squad's medic: the first eligible marine of the
     * middle fire team in roster order, else the first eligible marine at all.
     * The battle (live, not the leader) and the company card (ready, not the
     * NCO) both ask this, so the marine marked on the card is the one who treats.
     *
     * @param count    billets to consider, in roster order
     * @param eligible whether the billet at an index may carry the medic
     * @param fireTeam the fire team of the billet at an index
     * @return the chosen index, or -1 when nobody is eligible
     */
    public static int medicBillet(int count, IntPredicate eligible, IntUnaryOperator fireTeam) {
        int fallback = -1;
        for (int i = 0; i < count; i++) {
            if (!eligible.test(i)) continue;
            if (fireTeam.applyAsInt(i) == MEDIC_FIRE_TEAM) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }

    /** The medicine an officer of {@code rank} gives each squad, or null below rank one. */
    public static SquadMedicine forRank(int rank) {
        if (rank <= 0) return null;
        return rank == 1 ? RANK_ONE : RANK_TWO;
    }
}
