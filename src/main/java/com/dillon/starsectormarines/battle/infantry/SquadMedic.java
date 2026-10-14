package com.dillon.starsectormarines.battle.infantry;

/**
 * One squad's medic: who it is, what they have left, and the treatment in hand.
 *
 * <p>Written only by {@link SquadMedicSystem}'s serial pass, ahead of the
 * parallel unit dispatch, and only read during it. That split is what lets a
 * treatment raise somebody else's health without a worker ever writing to a
 * marine other than the one it is updating.
 */
public final class SquadMedic {

    /** Where the current treatment stands. */
    public enum Phase {
        /** No patient. */
        IDLE,
        /** A patient is chosen and the medic is walking to them; nothing spent yet. */
        APPROACHING,
        /** Kneeling over the patient; a supply is spent and restoration is landing. */
        TREATING
    }

    public final SquadMedicine medicine;
    /** Neither marine stops for a treatment; the medic follows the patient instead. */
    public final boolean keepMoving;

    long medicId;
    long patientId;
    Phase phase = Phase.IDLE;
    int supplies;
    float recoveryTimer;
    float cooldownTimer;
    float phaseTimer;

    int started;
    int completed;
    int interrupted;
    int armorReplated;
    float healthRestored;
    float armorRestored;

    SquadMedic(SquadMedicine medicine, boolean keepMoving) {
        this.medicine = medicine;
        this.keepMoving = keepMoving;
        this.supplies = medicine.supplies();
    }

    public long medicId() { return medicId; }
    public long patientId() { return patientId; }
    public Phase phase() { return phase; }
    public int supplies() { return supplies; }
    public float cooldownTimer() { return cooldownTimer; }

    /** Treatments begun, each spending one supply. */
    public int started() { return started; }
    /** Treatments that ran their full length or left the patient whole. */
    public int completed() { return completed; }
    /** Treatments ended early by death, separation, or the medic's fire team breaking. */
    public int interrupted() { return interrupted; }
    /** Times a treatment brought armor back from zero. */
    public int armorReplated() { return armorReplated; }
    public float healthRestored() { return healthRestored; }
    public float armorRestored() { return armorRestored; }

    /** How far through the current treatment, 0 to 1; 0 when not treating. */
    public float treatmentProgress() {
        if (phase != Phase.TREATING) return 0f;
        float channel = medicine.channelSeconds();
        return Math.max(0f, Math.min(1f, (channel - phaseTimer) / channel));
    }

    /** Whether {@code unit} is the medic with hands on a patient this tick. */
    public boolean isTreating(long unit) {
        return phase == Phase.TREATING && unit == medicId;
    }
}
