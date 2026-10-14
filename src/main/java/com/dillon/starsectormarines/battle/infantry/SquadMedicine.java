package com.dillon.starsectormarines.battle.infantry;

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

    /** The medicine an officer of {@code rank} gives each squad, or null below rank one. */
    public static SquadMedicine forRank(int rank) {
        if (rank <= 0) return null;
        return rank == 1 ? RANK_ONE : RANK_TWO;
    }
}
