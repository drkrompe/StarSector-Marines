package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Status;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

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
