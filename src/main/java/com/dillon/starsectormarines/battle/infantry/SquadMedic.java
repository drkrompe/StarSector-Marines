package com.dillon.starsectormarines.battle.infantry;

/**
 * One squad medic: who it is, the energy they have left, and the treatment in hand.
 * Either the officer's designated medic, re-designated by billet when they fall,
 * or, under an officer without Field Medicine, a field-aid kit's wearer, who is
 * the medic for as long as they live.
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
        /** Hands on the patient; restoration is landing and energy is being spent. */
        TREATING
    }

    public final SquadMedicine medicine;
    /** Whether this medic is a kit's wearer rather than the officer's billet. */
    public final boolean kit;

    long medicId;
    long patientId;
    Phase phase = Phase.IDLE;
    float energy;
    /** Seconds before the medic may set out again after abandoning an approach. */
    float retryTimer;
    /** Seconds on the approach under way. */
    float approachTimer;
    /** Seconds the treatment in hand has run. */
    float treatingSeconds;

    int started;
    int completed;
    int interrupted;
    int exhausted;
    int armorReplated;
    float healthRestored;
    float armorRestored;

    SquadMedic(SquadMedicine medicine) {
        this.medicine = medicine;
        this.kit = false;
        this.energy = medicine.energyMax();
    }

    /** The medic a field-aid kit makes of its wearer. */
    SquadMedic(SquadMedicine medicine, long wearer) {
        this.medicine = medicine;
        this.kit = true;
        this.medicId = wearer;
        this.energy = medicine.energyMax();
    }

    public long medicId() { return medicId; }
    public long patientId() { return patientId; }
    public Phase phase() { return phase; }
    /** Energy left in the pool. */
    public float energy() { return energy; }

    /** Treatments begun. */
    public int started() { return started; }
    /** Treatments that left the patient whole. */
    public int completed() { return completed; }
    /** Treatments ended by death, separation, or the medic's fire team breaking. */
    public int interrupted() { return interrupted; }
    /** Treatments that ended because the pool ran dry. */
    public int exhausted() { return exhausted; }
    /** Times a treatment brought armor back from zero. */
    public int armorReplated() { return armorReplated; }
    public float healthRestored() { return healthRestored; }
    public float armorRestored() { return armorRestored; }

    /** Seconds the current treatment has run; 0 when not treating. */
    public float treatmentSeconds() {
        return phase == Phase.TREATING ? treatingSeconds : 0f;
    }

    /** Whether {@code unit} is the medic with hands on a patient this tick. */
    public boolean isTreating(long unit) {
        return phase == Phase.TREATING && unit == medicId;
    }
}
