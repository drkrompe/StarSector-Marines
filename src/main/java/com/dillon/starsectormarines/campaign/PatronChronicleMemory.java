package com.dillon.starsectormarines.campaign;

/** Queries recent confirmed Chronicle facts linked to a patron house. */
public final class PatronChronicleMemory {

    public static final class Snapshot {
        public final long id;
        public final PatronChronicleReferenceType referenceType;
        public final long patronHouseId;
        public final long otherHouseId;
        public final ChainState chainOutcome;
        public final int happenedTick;
        public final int learnedTick;

        private Snapshot(long id,
                         PatronChronicleReferenceType referenceType,
                         long patronHouseId, long otherHouseId,
                         ChainState chainOutcome, int happenedTick,
                         int learnedTick) {
            this.id = id;
            this.referenceType = referenceType;
            this.patronHouseId = patronHouseId;
            this.otherHouseId = otherHouseId;
            this.chainOutcome = chainOutcome;
            this.happenedTick = happenedTick;
            this.learnedTick = learnedTick;
        }
    }

    private PatronChronicleMemory() {}

    /** Returns the newest eligible learned fact for a patron. */
    public static Snapshot latestForPatron(CampaignState state,
                                           long patronHouseId,
                                           int asOfDay,
                                           int maxAgeDays) {
        if (state == null || state.houseIndex(patronHouseId) < 0
                || asOfDay < 0 || maxAgeDays < 0) {
            return null;
        }
        int earliestDay = (int) Math.max(0L,
                (long) asOfDay - maxAgeDays);
        int newestRow = -1;
        PatronChronicleReferenceType newestType = null;
        for (int row = 0; row < state.chronicleCount; row++) {
            PatronChronicleReferenceType type = classify(
                    state, row, patronHouseId);
            int happenedDay = state.chronicleHappenedTick[row];
            int learnedDay = state.chronicleLearnedTick[row];
            if (type == null || happenedDay < earliestDay
                    || happenedDay > learnedDay || learnedDay > asOfDay) {
                continue;
            }
            if (newestRow < 0 || newer(state, row, newestRow)) {
                newestRow = row;
                newestType = type;
            }
        }
        if (newestRow < 0) return null;
        long actor = state.chronicleActorHouseId[newestRow];
        long target = state.chronicleTargetHouseId[newestRow];
        long otherHouseId = actor == patronHouseId ? target : actor;
        ChainState outcome = newestType == PatronChronicleReferenceType.CHAIN_ACTOR
                || newestType == PatronChronicleReferenceType.CHAIN_TARGET
                ? safeChainState(state.chronicleChainOutcome[newestRow])
                : null;
        return new Snapshot(state.chronicleId[newestRow], newestType,
                patronHouseId, otherHouseId, outcome,
                state.chronicleHappenedTick[newestRow],
                state.chronicleLearnedTick[newestRow]);
    }

    private static PatronChronicleReferenceType classify(
            CampaignState state, int row, long patronHouseId) {
        if (row < 0 || row >= state.chronicleCount
                || state.chronicleId[row] <= 0L
                || state.chronicleHappenedTick[row] < 0
                || state.chronicleLearnedTick[row] < 0
                || safeConfidence(state.chronicleConfidence[row])
                    != ChronicleConfidence.CONFIRMED
                || state.marketRegistry.get(
                    state.chronicleMarketId[row]) == null) {
            return null;
        }
        long actor = state.chronicleActorHouseId[row];
        long target = state.chronicleTargetHouseId[row];
        boolean patronIsActor = actor == patronHouseId;
        boolean patronIsTarget = target == patronHouseId;
        if (patronIsActor == patronIsTarget) return null;
        long otherHouseId = patronIsActor ? target : actor;
        if (!validHouse(state, patronHouseId)
                || !validHouse(state, otherHouseId)) {
            return null;
        }

        ChronicleEventType eventType = safeEventType(
                state.chronicleEventType[row]);
        if (eventType == ChronicleEventType.CHAIN_OUTCOME) {
            ChainState outcome = safeChainState(
                    state.chronicleChainOutcome[row]);
            if (state.chronicleSourceChainId[row] <= 0L
                    || (outcome != ChainState.RESOLVED
                        && outcome != ChainState.FAILED)) {
                return null;
            }
            return patronIsActor
                    ? PatronChronicleReferenceType.CHAIN_ACTOR
                    : PatronChronicleReferenceType.CHAIN_TARGET;
        }
        if (eventType == ChronicleEventType.THRONE_CLAIM_APPLIED) {
            if (state.chronicleSourceChainId[row] <= 0L
                    || state.factionRegistry.get(
                        state.chronicleSourceFactionId[row]) == null
                    || state.factionRegistry.get(
                        state.chronicleResultFactionId[row]) == null) {
                return null;
            }
            return patronIsActor
                    ? PatronChronicleReferenceType.THRONE_CLAIMANT
                    : PatronChronicleReferenceType.THRONE_DISPLACED;
        }
        if (eventType == ChronicleEventType.KINGMAKER_TESTAMENT) {
            if (state.chronicleSourceChainId[row] <= 0L
                    || state.chronicleTestamentId[row] <= 0L) {
                return null;
            }
            return patronIsActor
                    ? PatronChronicleReferenceType.TESTAMENT_CLAIMANT
                    : PatronChronicleReferenceType.TESTAMENT_DEPOSED;
        }
        return null;
    }

    private static boolean validHouse(CampaignState state, long houseId) {
        int row = state.houseIndex(houseId);
        if (row < 0) return false;
        String displayName = state.houseDisplayName[row];
        return displayName != null && !displayName.trim().isEmpty();
    }

    private static boolean newer(CampaignState state, int candidate,
                                 int reference) {
        int candidateLearned = state.chronicleLearnedTick[candidate];
        int referenceLearned = state.chronicleLearnedTick[reference];
        if (candidateLearned != referenceLearned) {
            return candidateLearned > referenceLearned;
        }
        int candidateHappened = state.chronicleHappenedTick[candidate];
        int referenceHappened = state.chronicleHappenedTick[reference];
        return candidateHappened > referenceHappened
                || (candidateHappened == referenceHappened
                    && state.chronicleId[candidate]
                        > state.chronicleId[reference]);
    }

    private static ChronicleEventType safeEventType(byte value) {
        try {
            return ChronicleEventType.fromByte(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static ChronicleConfidence safeConfidence(byte value) {
        try {
            return ChronicleConfidence.fromByte(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static ChainState safeChainState(byte value) {
        try {
            return ChainState.fromByte(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
