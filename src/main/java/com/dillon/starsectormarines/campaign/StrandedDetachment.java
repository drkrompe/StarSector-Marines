package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Status;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A garrison overrun in the player's absence whose survivors have gone to ground — see
 * {@code stranded-detachment-recovery.md}.
 *
 * <p>The stranded detachment is not a new roster or a new contract. It is the failed
 * stationing assignment held open past its settlement: the surviving squads stay bound to
 * the parent contract and the captain stays {@link Status#GARRISONED}, exactly as a
 * defaulted contract already holds its detachment until extraction resolves
 * ({@code contracts-nouns.md} law 4). {@code contractStrandedUntilTick} marks the row and
 * carries the recovery deadline.
 *
 * <p>The people come back only two ways: {@link #pickup} through the existing
 * {@code releaseStationing} path when the player's fleet reaches the market, or
 * {@link #expire} through {@code failStationingExtraction} when the deadline passes.
 * Neither touches the market, the employer or the attacker.
 */
public final class StrandedDetachment {

    /** Days the survivors can hold out before nobody is left to collect. A first proposal. */
    public static final int RECOVERY_DAYS = 30;

    /** Days between losses while a detachment waits. A first proposal. */
    public static final int ATTRITION_INTERVAL_DAYS = 10;

    /** Matches a failed extraction after employer default. */
    static final float LOST_CAPTAIN_INJURY_DAYS = 45f;

    private StrandedDetachment() {}

    /** Whether this contract row holds a detachment waiting to be collected. */
    public static boolean isStranded(CampaignState state, int row) {
        return state != null && row >= 0 && row < state.contractCount
                && ContractType.fromByte(state.contractType[row]).isStationing()
                && ContractState.fromByte(state.contractState[row]) == ContractState.FAILED
                && state.contractStrandedUntilTick[row] >= 0;
    }

    /** Contract ids of every detachment stranded on the market with vanilla id {@code marketId}. */
    public static List<Long> strandedAt(CampaignState state, String marketId) {
        List<Long> ids = new ArrayList<>();
        if (state == null || marketId == null) return ids;
        for (int row = 0; row < state.contractCount; row++) {
            if (isStranded(state, row)
                    && marketId.equals(state.marketRegistry.get(state.contractMarketId[row]))) {
                ids.add(state.contractId[row]);
            }
        }
        return ids;
    }

    /** Starts the recovery clock on a failed row that still holds its survivors. */
    static void strand(CampaignState state, int row, int day) {
        state.contractStrandedUntilTick[row] = day + RECOVERY_DAYS;
        state.contractStrandedIntervals[row] = 0;
    }

    /**
     * Applies every attrition interval that has elapsed by {@code day}: each one lists one
     * living marine per stranded squad as missing. Who is struck is seeded from the
     * contract, its defence event and the interval index, so a replayed day strikes the
     * same people, and the applied-interval watermark keeps an interval from striking
     * twice. Missing, never killed: nobody saw them die. The captain is never struck
     * here; their fate is settled by the pickup or the deadline.
     *
     * <p>The interval that falls on the deadline itself is left to {@link #expire}.
     *
     * @return the names of the marines listed missing today, in order
     */
    public static List<String> attrition(CampaignState state, int row, MarineRoster roster,
                                         int day) {
        List<String> lost = new ArrayList<>();
        if (!isStranded(state, row) || roster == null) return lost;
        int deadline = state.contractStrandedUntilTick[row];
        int start = deadline - RECOVERY_DAYS;
        int due = Math.max(0, Math.min(day, deadline - 1) - start) / ATTRITION_INTERVAL_DAYS;
        long contractId = state.contractId[row];
        while (state.contractStrandedIntervals[row] < due) {
            int interval = ++state.contractStrandedIntervals[row];
            Random seed = new Random(contractId * 1_000_003L
                    ^ state.contractDefenseEventKey[row] * 31L ^ interval);
            for (MarineSquad squad : roster.squadsStationedOn(contractId)) {
                List<MarineSoldier> living = new ArrayList<>();
                for (MarineSoldier soldier : roster.squadMembers(squad)) {
                    if (soldier.status() == MarineSoldierStatus.ACTIVE
                            || soldier.status() == MarineSoldierStatus.WIA) {
                        living.add(soldier);
                    }
                }
                if (living.isEmpty()) continue;
                MarineSoldier struck = living.get(seed.nextInt(living.size()));
                if (roster.listStationedMissing(contractId, struck.id())) lost.add(struck.name());
            }
        }
        state.contractMarinesCommitted[row] = roster.stationedLivingCount(contractId);
        return lost;
    }

    /**
     * Collects the detachment: survivors and captain come home through the one release
     * path, and the company records the recovery.
     *
     * @param placeName the market's display name, or null when it cannot be read
     * @return the marines recovered, or -1 when nothing was stranded on that contract
     */
    public static int pickup(CampaignState state, long contractId, MarineRoster roster,
                             int day, String placeName) {
        int row = state != null ? state.contractIndex(contractId) : -1;
        if (!isStranded(state, row) || roster == null) return -1;
        int living = roster.stationedLivingCount(contractId);
        MarineCaptain captain = StationingLapseResolution.assignedCaptain(
                state, contractId, roster);
        String where = place(placeName);
        roster.releaseStationing(contractId);
        if (captain != null && captain.status() == Status.GARRISONED) {
            captain.setStatus(Status.ACTIVE);
            captain.commendations().add("Day " + day + ": Recovered from " + where
                    + " after the garrison was overrun.");
        }
        clear(state, row);
        String who = captain != null ? captain.name()
                : Strings.get("strandedDetachmentCompany");
        state.addChronicleDetachmentOutcome(ChronicleEventType.DETACHMENT_RECOVERED,
                contractId, state.contractMarketId[row], living, living,
                MessageFormat.format(Strings.get("strandedDetachmentRecoveredAccount"),
                        who, living, where), day, day);
        return living;
    }

    /**
     * Nobody came in time: every living member goes missing, the captain comes home
     * injured, and the company records the loss.
     *
     * @return the marines lost, or -1 when nothing was stranded on that contract
     */
    public static int expire(CampaignState state, int row, MarineRoster roster, int day,
                             String placeName) {
        if (!isStranded(state, row) || roster == null) return -1;
        long contractId = state.contractId[row];
        int living = roster.stationedLivingCount(contractId);
        MarineCaptain captain = StationingLapseResolution.assignedCaptain(
                state, contractId, roster);
        String where = place(placeName);
        roster.failStationingExtraction(contractId);
        if (captain != null && captain.status() == Status.GARRISONED) {
            captain.setStatus(Status.INJURED);
            captain.setInjuredUntilDay(day + LOST_CAPTAIN_INJURY_DAYS);
            captain.commendations().add("Day " + day + ": Made it out of " + where
                    + " alone after the stranded garrison could not be recovered.");
        }
        clear(state, row);
        state.addChronicleDetachmentOutcome(ChronicleEventType.DETACHMENT_LOST,
                contractId, state.contractMarketId[row], living, 0,
                MessageFormat.format(Strings.get("strandedDetachmentLostAccount"),
                        where, living), day, day);
        return living;
    }

    private static void clear(CampaignState state, int row) {
        state.contractMarinesCommitted[row] = 0;
        state.contractCaptainId[row] = -1;
        state.contractStrandedUntilTick[row] = -1;
    }

    private static String place(String placeName) {
        return placeName != null && !placeName.isBlank()
                ? placeName : Strings.get("defenceReportUnknownPlace");
    }
}
