package com.dillon.starsectormarines.campaign;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each held military warrant costs to keep ({@code progression-nouns.md}, Kit and armory):
 * one term per issuing tradition, with its obligation period clock and standing.
 *
 * <p>A term records only what the warrant's obligations need: when the current period began,
 * whether loyalty has been broken in it, and which contracts have already been counted against
 * the issuer, so a contract is never counted twice. Service is no longer asked: the campaign
 * offers no generic contracts to serve with ({@code contracts-nouns.md}).
 */
public final class WarrantObligationLedger implements Serializable {

    /** A warrant's standing under its obligations. */
    public enum Status {
        /** Terms kept: the warrant opens its suits. */
        ACTIVE,
        /** Broken loyalty: no new acquisition or fitting until one honoured period. */
        SUSPENDED,
        /** Relations fell to hostile: suspended permanently, until a new warrant is granted. */
        REVOKED
    }

    /** One issuer's obligations. */
    public static final class Term implements Serializable {
        private Status status = Status.ACTIVE;
        private final int openedDay;
        private int periodStart;
        /** Retired with service calls; kept so older saves load. */
        @Deprecated
        private boolean serviceDone;
        private boolean loyaltyBroken;
        /** Retired with service calls; kept so older saves load. */
        @Deprecated
        private long serviceCallId = -1L;
        /** Retired with service calls; kept so older saves load. */
        @Deprecated
        private int serviceCallDeadline = -1;
        private int honouredPeriods;
        private int statusDay;
        private final Set<Long> counted = new HashSet<>();

        Term(int day) {
            openedDay = day;
            periodStart = day;
            statusDay = day;
        }

        public Status status() { return status; }
        /** The day the terms were first stated; contracts accepted before it are never held against them. */
        public int openedDay() { return openedDay; }
        public int periodStart() { return periodStart; }
        public boolean loyaltyBroken() { return loyaltyBroken; }
        public int honouredPeriods() { return honouredPeriods; }
        /** The day the current status began. */
        public int statusDay() { return statusDay; }

        /** Counts {@code contractId} against the issuer once; true when this call counted it. */
        public boolean breakLoyalty(long contractId) {
            if (!counted.add(contractId)) return false;
            loyaltyBroken = true;
            return true;
        }

        /** Loyalty broken by standing rather than by a contract. */
        public void breakLoyalty() { loyaltyBroken = true; }

        /** A suspended period that ended with loyalty broken: still suspended, with a fresh period. */
        public void restartPeriod(int day) {
            beginPeriod(day);
        }

        /** An honoured period: the warrant stands (restored if suspended) and the next period begins. */
        public void honour(int day) {
            honouredPeriods++;
            if (status != Status.ACTIVE) statusDay = day;
            if (status != Status.REVOKED) status = Status.ACTIVE;
            beginPeriod(day);
        }

        /** Broken loyalty: suspended, with a fresh period to earn restoration in. */
        public void suspend(int day) {
            if (status != Status.ACTIVE) return;
            status = Status.SUSPENDED;
            statusDay = day;
            beginPeriod(day);
        }

        public void revoke(int day) {
            if (status == Status.REVOKED) return;
            status = Status.REVOKED;
            statusDay = day;
        }

        /** A new warrant from the same issuer replaces revoked terms. */
        public void regrant(int day) {
            status = Status.ACTIVE;
            statusDay = day;
            beginPeriod(day);
        }

        private void beginPeriod(int day) {
            periodStart = day;
            loyaltyBroken = false;
        }
    }

    private final Map<String, Term> terms = new LinkedHashMap<>();

    /** The issuer's term, or null when it holds no warrant yet. */
    public Term term(String issuerKey) {
        return terms.get(issuerKey);
    }

    /** The issuer's term, stating it from {@code day} when this is the first warrant it granted. */
    public Term open(String issuerKey, int day) {
        return terms.computeIfAbsent(issuerKey, key -> new Term(day));
    }

    /** Issuers whose terms put their warrant out of use. */
    public Set<String> withheldIssuers() {
        Set<String> withheld = new HashSet<>();
        for (Map.Entry<String, Term> entry : terms.entrySet()) {
            if (entry.getValue().status() != Status.ACTIVE) withheld.add(entry.getKey());
        }
        return withheld;
    }

    public Collection<String> issuers() {
        return List.copyOf(terms.keySet());
    }
}
