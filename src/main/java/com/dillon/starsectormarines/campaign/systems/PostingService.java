package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.Posting;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates and ends a <b>posting</b>: the player's own detachment bound to one of the
 * player's own markets with no employer ({@code contracts-nouns.md}).
 *
 * <p>This is {@link StationingAssignmentService} with the commercial half removed. It
 * writes the same activation set, minus everything a patron would have supplied — no
 * rank-derived terms, no retainer, no term boundary, no salvage entitlement — and it
 * never touches reputation, because there is nobody on the other side of it.
 */
public final class PostingService {

    private PostingService() {}

    /**
     * Binds {@code squadIds} and {@code captain} to a new posting at {@code marketId}.
     *
     * <p>At most one posting stands at a market: a second is refused rather than
     * merged into the first.
     *
     * @return the new contract id, or {@code -1L} when nothing was posted
     */
    public static long post(CampaignState state, String marketId, MarineRoster roster,
                            MarineCaptain captain, Collection<String> squadIds, int day) {
        if (state == null || marketId == null || marketId.isBlank()
                || roster == null || captain == null || squadIds == null
                || roster.byId(captain.id()) == null
                || captain.status() != Status.ACTIVE) {
            return -1L;
        }
        int marketSlot = state.marketRegistry.intern(marketId);
        if (Posting.activeRowAt(state, marketSlot) >= 0) return -1L;

        List<String> selected = new ArrayList<>(squadIds);
        long contractId = state.addContract(Posting.NO_PATRON, -1L, -1L,
                ContractType.GARRISON, ContractState.ACTIVE, day, -1, -1,
                (byte) 0, -1, marketSlot, -1, 0, 0,
                (byte) 0, (byte) 0, (byte) 100);
        if (!roster.bindStationing(contractId, captain.id(), selected)) {
            // The row cannot be un-appended, so retire it instead: a terminal row that
            // owns no personnel is what ContractTableCompactor exists to drop.
            state.contractState[state.contractIndex(contractId)] =
                    ContractState.EXPIRED.toByte();
            return -1L;
        }
        activate(state, contractId, marketId, captain, roster, day);
        return contractId;
    }

    /**
     * Returns a posting's detachment and captain to the roster and closes the row.
     *
     * <p>Refused while a defence is armed: a posting under an armed defence is real
     * unpaid work, and a response that stands cannot be walked away from
     * ({@code contracts-nouns.md}, law 5). Arming moves the row to
     * {@code IN_PROGRESS}, so the state gate carries that on its own; the payload
     * check beside it states the same law independently rather than resting on which
     * state a future producer happens to leave the row in.
     *
     * <p>The row settles {@code COMPLETED} rather than {@code ABANDONED} — nobody was
     * let down — and no reputation is written, because a posting has no employer to
     * have an opinion.
     */
    public static boolean release(CampaignState state, long contractId,
                                  MarineRoster roster, int day) {
        if (state == null || roster == null) return false;
        int row = state.contractIndex(contractId);
        if (row < 0 || !Posting.isPosting(state, row)
                || ContractState.fromByte(state.contractState[row]) != ContractState.ACTIVE
                || GarrisonDefensePayload.from(state, contractId, roster) != null) {
            return false;
        }

        roster.releaseStationing(contractId);
        int captainSlot = state.contractCaptainId[row];
        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        MarineCaptain captain = captainId != null ? roster.byId(captainId) : null;
        if (captain != null && captain.status() == Status.GARRISONED) {
            captain.setStatus(Status.ACTIVE);
            captain.commendations().add("Day " + day + ": Released from the posting at "
                    + marketName(state, row) + ".");
        }
        state.contractMarinesCommitted[row] = 0;
        state.contractCaptainId[row] = -1;
        state.contractState[row] = ContractState.COMPLETED.toByte();
        return true;
    }

    /**
     * The activation set {@link StationingAssignmentService} writes, minus the terms.
     * Written out rather than left to {@code addContract}'s defaults so the shape of a
     * posting row is stated in one place and stays comparable with a Garrison's.
     */
    private static void activate(CampaignState state, long contractId, String marketId,
                                 MarineCaptain captain, MarineRoster roster, int day) {
        int row = state.contractIndex(contractId);
        state.contractState[row] = ContractState.ACTIVE.toByte();
        state.contractAcceptedTick[row] = day;
        state.contractExpiresTick[row] = -1;
        state.contractOfferExpiresTick[row] = -1;
        state.contractPhasesTotal[row] = 0;
        state.contractCaptainId[row] = state.captainRegistry.intern(captain.id());
        state.contractBasePayout[row] = 0;
        state.contractRetainerPerMonth[row] = 0;
        state.contractMarinesCommitted[row] = roster.stationedLivingCount(contractId);
        state.contractLastRetainerTick[row] = day;
        state.contractLastTrainingTick[row] = -1;
        state.contractLastDefaultCheckTick[row] = day;
        state.contractNextIncidentTick[row] = -1;
        state.contractIncidentPending[row] = 0;
        state.contractIncidentType[row] = StationingIncidentType.NONE.toByte();
        state.contractDefenseEventKey[row] = 0L;
        state.contractDefenseTriggeredTick[row] = -1;
        state.contractDefenseTriggerType[row] = GarrisonDefenseTriggerType.NONE.toByte();
        state.contractDefenseAttackerHouseId[row] = -1L;
        state.contractDefenseAttackerFactionId[row] = -1;
        state.contractDefenseAttackerStrength[row] = 0f;
        state.contractSalvageBaseline[row] = 0;
        state.contractSalvageNegotiated[row] = 0;
        state.contractCashMultiplier[row] = (byte) 100;
        captain.setStatus(Status.GARRISONED);
        captain.commendations().add("Day " + day + ": Posted to " + marketId + ".");
    }

    private static String marketName(CampaignState state, int row) {
        String name = state.marketRegistry.get(state.contractMarketId[row]);
        return name != null ? name : "an unnamed world";
    }
}
