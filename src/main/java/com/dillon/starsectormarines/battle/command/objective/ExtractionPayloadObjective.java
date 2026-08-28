package com.dillon.starsectormarines.battle.command.objective;

/**
 * Read-only mission contract shared by Extraction-family objective variants.
 * Implementations retain their own movement and terminal law; this interface
 * exposes only the stable facts needed by disclosure, diagnostics, and neutral
 * evidence.
 */
public interface ExtractionPayloadObjective extends Objective {

    enum Kind { PACKAGE, COHORT, ARCHIVE }

    enum Phase {
        AT_SOURCE,
        SECURING,
        IN_TRANSIT,
        BOARDING,
        COMPLETE,
        FAILED
    }

    enum Failure { NONE, LOST, ABANDONED }

    String payloadId();

    String payloadName();

    Kind payloadKind();

    int sourceCellX();

    int sourceCellY();

    /** Returns {@code -1} when this branch does not own an egress endpoint. */
    int egressCellX();

    /** Returns {@code -1} when this branch does not own an egress endpoint. */
    int egressCellY();

    /** Current authoritative payload cell, or {@code -1} when not represented. */
    int payloadCellX();

    /** Current authoritative payload cell, or {@code -1} when not represented. */
    int payloadCellY();

    int initialElements();

    int activeElements();

    /** Boarded or otherwise secured elements, or {@code -1} when inapplicable. */
    int boardedElements();

    int lostElements();

    /** Normalized mission progress in {@code [0, 1]}. */
    float normalizedProgress();

    Phase extractionPhase();

    Failure failureReason();

    boolean alarmActive();

    int alarmRaisedTick();

    /** Squad currently satisfying the control/escort relation, or {@code -1}. */
    int controllingSquadId();

    boolean escortPresent();
}
