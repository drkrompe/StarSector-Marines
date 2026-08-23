package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Everything with a deadline running against the company, soonest first.
 *
 * <p>Derived on demand, never persisted, and free of {@code Global} so the whole
 * ordering and inclusion rule set is headless-testable.
 *
 * <h2>Obligations only</h2>
 *
 * <p>This list carries deadlines that <b>bite</b> — a lapsed response costs reputation
 * and loses the garrison; a term boundary stops the retainer and can fail the contract
 * outright. Lapsing <em>offers</em> are deliberately excluded: a missed offer costs
 * nothing but the job, and its only useful action is "fly somewhere else", which is the
 * contract board's job rather than this pane's. See {@code company-view-nouns.md}
 * and {@code c11-the-contract-board.md}.
 *
 * <h2>One contract can own two clocks</h2>
 *
 * <p>A stationing contract with a pending response and a term boundary produces two
 * entries, because they are two different deadlines with two different consequences and
 * two different answers. The term entry knows when the response is what will fail it —
 * see {@link Entry#failsOnExpiry}.
 */
public final class CompanyClocks {

    public enum Kind {
        /** A stationed detachment is waiting on the player to answer something. */
        RESPONSE,
        /** A stationing term is running out. */
        TERM_ENDING
    }

    /** One deadline, flattened for rendering. */
    public static final class Entry {
        public final Kind kind;
        public final long contractId;
        /** Campaign day the deadline falls on. */
        public final int deadlineDay;
        /** Market registry slot this clock concerns, or -1. */
        public final int marketId;
        /** Employer display name, or null when the house has no name in the registry. */
        public final String patronName;
        /**
         * The underlying notice for a {@link Kind#RESPONSE} entry, so the pane can route
         * Respond through the same path the event popup's Deploy takes. Null for every
         * other kind.
         */
        public final PlayerEventNotice notice;
        /**
         * True when reaching this term boundary would fail the contract rather than
         * complete it — the G31 invariant that an unanswered response can never be
         * laundered into a successful completion. Always false for a response entry.
         */
        public final boolean failsOnExpiry;

        Entry(Kind kind, long contractId, int deadlineDay, int marketId,
              String patronName, PlayerEventNotice notice, boolean failsOnExpiry) {
            this.kind = kind;
            this.contractId = contractId;
            this.deadlineDay = deadlineDay;
            this.marketId = marketId;
            this.patronName = patronName;
            this.notice = notice;
            this.failsOnExpiry = failsOnExpiry;
        }

        /** Days left to act, floored at zero — an overdue clock reads as due, never negative. */
        public int daysRemaining(int currentDay) {
            return Math.max(0, deadlineDay - currentDay);
        }

        @Override
        public String toString() {
            return "CompanyClocks.Entry[" + kind + " contract=" + contractId
                    + " deadline=" + deadlineDay + "]";
        }
    }

    private CompanyClocks() {}

    /**
     * Live-world convenience for the company view. Resolves the campaign state, roster,
     * and day from the running game; {@link #rows} stays pure so the rule set is
     * testable without a Sector.
     */
    public static List<Entry> current() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return Collections.emptyList();
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        return rows(script.state(),
                rosterScript != null ? rosterScript.roster() : null,
                CampaignClock.day());
    }

    /**
     * Every live obligation deadline, soonest first. Ties break on contract id and then
     * on kind, so a contract that owns both clocks always renders its response above its
     * term boundary rather than swapping between frames.
     */
    public static List<Entry> rows(CampaignState state, MarineRoster roster, int day) {
        if (state == null) return Collections.emptyList();

        List<Entry> entries = new ArrayList<>();
        for (PlayerEventNotice notice : PlayerEventInbox.pending(state, roster, day)) {
            entries.add(new Entry(Kind.RESPONSE, notice.contractId, notice.deadlineDay,
                    notice.marketId, patronName(state, notice.contractId), notice, false));
        }
        for (int row = 0; row < state.contractCount; row++) {
            if (!liveStationingRow(state, row)) continue;
            int expires = state.contractExpiresTick[row];
            if (expires < 0) continue;
            long contractId = state.contractId[row];
            entries.add(new Entry(Kind.TERM_ENDING, contractId, expires,
                    state.contractMarketId[row], patronName(state, contractId), null,
                    hasPendingResponse(entries, contractId)));
        }

        entries.sort(Comparator.comparingInt((Entry e) -> e.deadlineDay)
                .thenComparingLong(e -> e.contractId)
                .thenComparingInt(e -> e.kind.ordinal()));
        return entries;
    }

    /**
     * Only assignments that are actually running. An OFFERED row has not been accepted
     * and a terminal row has already resolved — neither has a term ticking down, and
     * showing one would put a countdown on the screen that nothing will ever fire.
     */
    private static boolean liveStationingRow(CampaignState state, int row) {
        if (!ContractType.fromByte(state.contractType[row]).isStationing()) return false;
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        return contractState == ContractState.ACTIVE
                || contractState == ContractState.IN_PROGRESS;
    }

    /** Scans the response entries already collected for this same contract. */
    private static boolean hasPendingResponse(List<Entry> collected, long contractId) {
        for (Entry entry : collected) {
            if (entry.kind == Kind.RESPONSE && entry.contractId == contractId) return true;
        }
        return false;
    }

    private static String patronName(CampaignState state, long contractId) {
        int row = state.contractIndex(contractId);
        if (row < 0) return null;
        int houseRow = state.houseIndex(state.contractPatronHouseId[row]);
        return houseRow < 0 ? null : state.houseDisplayName[houseRow];
    }
}
