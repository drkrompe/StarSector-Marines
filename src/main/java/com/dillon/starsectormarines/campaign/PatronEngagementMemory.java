package com.dillon.starsectormarines.campaign;

/** Records and queries immutable, contract-backed player/patron history. */
public final class PatronEngagementMemory {

    public static final class History {
        public final Snapshot latest;
        public final Snapshot previous;
        public final int engagementCount;

        private History(Snapshot latest, Snapshot previous,
                        int engagementCount) {
            this.latest = latest;
            this.previous = previous;
            this.engagementCount = engagementCount;
        }
    }

    public static final class Snapshot {
        public final long id;
        public final long sourceContractId;
        public final long houseId;
        public final ContractType contractType;
        public final int marketId;
        public final PatronEngagementOutcome outcome;
        public final int happenedTick;
        public final int priorEngagementCount;

        private Snapshot(long id, long sourceContractId, long houseId,
                         ContractType contractType, int marketId,
                         PatronEngagementOutcome outcome, int happenedTick,
                         int priorEngagementCount) {
            this.id = id;
            this.sourceContractId = sourceContractId;
            this.houseId = houseId;
            this.contractType = contractType;
            this.marketId = marketId;
            this.outcome = outcome;
            this.happenedTick = happenedTick;
            this.priorEngagementCount = priorEngagementCount;
        }
    }

    private PatronEngagementMemory() {}

    /** Records the first valid terminal snapshot for a source contract. */
    public static long record(CampaignState state, long sourceContractId,
                              PatronEngagementOutcome outcome, int day) {
        if (state == null || sourceContractId <= 0L || outcome == null
                || day < 0) {
            return -1L;
        }
        int recordedRow = sourceRow(state, sourceContractId);
        if (recordedRow >= 0) return state.patronEngagementId[recordedRow];

        int contractRow = state.contractIndex(sourceContractId);
        if (contractRow < 0) return -1L;
        ContractState terminal = safeContractState(
                state.contractState[contractRow]);
        ContractType type = safeContractType(state.contractType[contractRow]);
        long patronId = state.contractPatronHouseId[contractRow];
        int marketId = state.contractMarketId[contractRow];
        if (terminal == null || type == null
                || terminal != terminalState(outcome)
                || patronId <= 0L || state.houseIndex(patronId) < 0
                || state.marketRegistry.get(marketId) == null) {
            return -1L;
        }
        return state.appendPatronEngagement(sourceContractId, patronId, type,
                marketId, outcome, day);
    }

    static boolean hasSource(CampaignState state, long sourceContractId) {
        return sourceRow(state, sourceContractId) >= 0;
    }

    private static int sourceRow(CampaignState state, long sourceContractId) {
        if (state == null || sourceContractId <= 0L) return -1;
        for (int row = 0; row < state.patronEngagementCount; row++) {
            if (state.patronEngagementSourceContractId[row]
                    == sourceContractId) {
                return row;
            }
        }
        return -1;
    }

    /** Returns the newest valid snapshot for a patron, or {@code null}. */
    public static Snapshot latest(CampaignState state, long houseId) {
        History history = history(state, houseId);
        return history != null ? history.latest : null;
    }

    /** Returns the newest two valid snapshots, ordered newest first. */
    public static History history(CampaignState state, long houseId) {
        if (state == null || houseId <= 0L) return null;
        int latestRow = -1;
        int previousRow = -1;
        int count = 0;
        for (int row = 0; row < state.patronEngagementCount; row++) {
            if (state.patronEngagementHouseId[row] != houseId
                    || !validSnapshotRow(state, row)) {
                continue;
            }
            count++;
            if (latestRow < 0 || newer(state, row, latestRow)) {
                previousRow = latestRow;
                latestRow = row;
            } else if (previousRow < 0
                    || newer(state, row, previousRow)) {
                previousRow = row;
            }
        }
        if (latestRow < 0) return null;
        return new History(snapshot(state, latestRow, count),
                previousRow >= 0 ? snapshot(state, previousRow, count) : null,
                count);
    }

    /** Returns the newest recent engagement for another house at a market. */
    public static Snapshot latestOtherAtMarket(
            CampaignState state, int marketId, long excludedHouseId,
            int asOfDay, int maxAgeDays) {
        if (state == null || state.marketRegistry.get(marketId) == null
                || excludedHouseId <= 0L || asOfDay < 0
                || maxAgeDays < 0) {
            return null;
        }
        int earliestDay = (int) Math.max(0L,
                (long) asOfDay - maxAgeDays);
        int latestRow = -1;
        for (int row = 0; row < state.patronEngagementCount; row++) {
            int happenedDay = state.patronEngagementHappenedTick[row];
            if (state.patronEngagementHouseId[row] == excludedHouseId
                    || state.patronEngagementMarketId[row] != marketId
                    || happenedDay < earliestDay || happenedDay > asOfDay
                    || !validSnapshotRow(state, row)) {
                continue;
            }
            if (latestRow < 0 || newer(state, row, latestRow)) {
                latestRow = row;
            }
        }
        if (latestRow < 0) return null;
        long houseId = state.patronEngagementHouseId[latestRow];
        return snapshot(state, latestRow, validCount(state, houseId));
    }

    private static int validCount(CampaignState state, long houseId) {
        int count = 0;
        for (int row = 0; row < state.patronEngagementCount; row++) {
            if (state.patronEngagementHouseId[row] == houseId
                    && validSnapshotRow(state, row)) {
                count++;
            }
        }
        return count;
    }

    private static Snapshot snapshot(CampaignState state, int row, int count) {
        return new Snapshot(state.patronEngagementId[row],
                state.patronEngagementSourceContractId[row],
                state.patronEngagementHouseId[row],
                safeContractType(state.patronEngagementContractType[row]),
                state.patronEngagementMarketId[row],
                safeOutcome(state.patronEngagementOutcome[row]),
                state.patronEngagementHappenedTick[row], count);
    }

    private static boolean newer(CampaignState state, int candidate,
                                 int reference) {
        return state.patronEngagementHappenedTick[candidate]
                    > state.patronEngagementHappenedTick[reference]
                || (state.patronEngagementHappenedTick[candidate]
                        == state.patronEngagementHappenedTick[reference]
                    && state.patronEngagementId[candidate]
                        > state.patronEngagementId[reference]);
    }

    private static boolean validSnapshotRow(CampaignState state, int row) {
        return row >= 0 && row < state.patronEngagementCount
                && state.patronEngagementId[row] > 0L
                && state.patronEngagementSourceContractId[row] > 0L
                && state.patronEngagementHouseId[row] > 0L
                && state.houseIndex(state.patronEngagementHouseId[row]) >= 0
                && safeContractType(
                    state.patronEngagementContractType[row]) != null
                && state.marketRegistry.get(
                    state.patronEngagementMarketId[row]) != null
                && safeOutcome(state.patronEngagementOutcome[row]) != null
                && state.patronEngagementHappenedTick[row] >= 0;
    }

    private static ContractState terminalState(PatronEngagementOutcome outcome) {
        switch (outcome) {
            case COMPLETED: return ContractState.COMPLETED;
            case FAILED: return ContractState.FAILED;
            case WITHDREW: return ContractState.ABANDONED;
            case EMPLOYER_BREACHED: return ContractState.DEFAULTED;
            default: return null;
        }
    }

    private static ContractState safeContractState(byte value) {
        try {
            return ContractState.fromByte(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static ContractType safeContractType(byte value) {
        try {
            return ContractType.fromByte(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static PatronEngagementOutcome safeOutcome(byte value) {
        try {
            return PatronEngagementOutcome.fromByte(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
