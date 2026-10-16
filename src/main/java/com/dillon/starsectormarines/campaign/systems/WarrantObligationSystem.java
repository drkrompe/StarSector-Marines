package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.WarrantObligationLedger;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.HegemonyAuxiliaryWarrant;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.EnumSet;
import java.util.Random;
import java.util.function.Function;

/**
 * Keeps each held military warrant's obligations ({@code progression-nouns.md}, Kit and armory):
 * the period clock, service and loyalty, the service call, suspension and revocation.
 *
 * <p>Each obligation period asks for <em>service</em> (a contract completed for the issuer, a
 * service call among them) and <em>loyalty</em> (no contract against the issuer, and the
 * relationship never below the warrant's floor). A period with both is honoured; one without
 * suspends the warrant, and one honoured period restores it. A hostile relationship revokes it.
 * Nothing learned is revoked and no fitted suit is taken away: suspension only withholds new
 * acquisition and new fitting.
 */
public final class WarrantObligationSystem implements CampaignSystem {

    /** Campaign days in one obligation period. */
    public static final int PERIOD_DAYS = 90;

    /** Days before a period ends at which the issuer sends a service call, if none is done. */
    public static final int SERVICE_CALL_LEAD_DAYS = 30;

    private static final int SERVICE_CALL_ROLLS = 8;

    private final Function<String, RepLevel> relationship;

    public WarrantObligationSystem() {
        this(WarrantObligationSystem::liveRelationship);
    }

    WarrantObligationSystem(Function<String, RepLevel> relationship) {
        this.relationship = relationship;
    }

    @Override
    public String name() {
        return "WarrantObligation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.PLAYER_REP, CampaignTable.MILITARY_WARRANTS,
                CampaignTable.HOUSES, CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    /**
     * The relationship below which a warrant's loyalty term is broken: Favorable for an
     * authored warrant, the standing rule's own threshold for a standing one.
     */
    public static RepLevel floor(MilitaryWarrants.Reason reason) {
        return reason == MilitaryWarrants.Reason.STORY
                ? HegemonyAuxiliaryWarrant.RELATIONSHIP_FLOOR : MilitaryWarrants.STANDING_RELATIONSHIP;
    }

    @Override
    public void tick(CampaignState state, int day) {
        WarrantObligationLedger ledger = state.warrantObligations();
        for (MilitaryWarrants.Held held : MilitaryWarrants.held(state)) {
            ArmorTradition issuer = held.issuer();
            boolean opened = ledger.term(issuer.key) == null;
            WarrantObligationLedger.Term term = ledger.open(issuer.key, day);
            if (opened) announce("warrantObligationTermsStated", issuer, PERIOD_DAYS);
            if (term.status() == WarrantObligationLedger.Status.REVOKED) continue;

            RepLevel rel = relationship.apply(issuer.key);
            if (rel != null && rel.isAtBest(RepLevel.HOSTILE)) {
                term.revoke(day);
                announce("warrantObligationRevoked", issuer, 0);
                continue;
            }
            boolean atFloor = rel != null && rel.isAtWorst(floor(held.reason()));
            if (!atFloor) term.breakLoyalty();
            scanContracts(state, issuer.key, term);

            if (term.loyaltyBroken() && term.status() == WarrantObligationLedger.Status.ACTIVE) {
                term.suspend(day);
                announce("warrantObligationSuspendedLoyalty", issuer, 0);
                if (!atFloor) term.breakLoyalty();
            }
            if (term.status() == WarrantObligationLedger.Status.SUSPENDED) {
                if (term.serviceDone() && atFloor) {
                    term.honour(day);
                    announce("warrantObligationRestored", issuer, PERIOD_DAYS);
                    honoured(state, issuer, day);
                }
            } else if (day >= term.periodStart() + PERIOD_DAYS) {
                if (term.serviceDone() && atFloor && !term.loyaltyBroken()) {
                    term.honour(day);
                    honoured(state, issuer, day);
                } else {
                    term.suspend(day);
                    announce("warrantObligationSuspendedService", issuer, 0);
                }
            }
            maybeSendServiceCall(state, issuer, term, day);
        }
    }

    /** The day the term's current period ends: its next deadline. */
    public static int periodEnd(WarrantObligationLedger.Term term) {
        return term.periodStart() + PERIOD_DAYS;
    }

    private static void honoured(CampaignState state, ArmorTradition issuer, int day) {
        if (issuer == ArmorTradition.HEGEMONY && HegemonyAuxiliaryWarrant.honouredPeriod(state, day)) {
            announce("warrantObligationAuxiliaryStageTwo", issuer, 0);
        }
    }

    /** Credits contracts completed for the issuer, and counts contracts taken against it. */
    private static void scanContracts(CampaignState state, String issuerKey, WarrantObligationLedger.Term term) {
        for (int i = 0; i < state.contractCount; i++) {
            ContractState contract = ContractState.fromByte(state.contractState[i]);
            if (state.contractAcceptedTick[i] < term.openedDay()) continue;
            if (contract == ContractState.COMPLETED
                    && issuerKey.equals(houseFaction(state, state.contractPatronHouseId[i]))) {
                term.creditService(state.contractId[i]);
            }
            boolean taken = contract == ContractState.ACTIVE || contract == ContractState.IN_PROGRESS
                    || contract == ContractState.COMPLETED;
            if (taken && issuerKey.equals(houseFaction(state, state.contractTargetHouseId[i]))) {
                term.breakLoyalty(state.contractId[i]);
            }
        }
    }

    /**
     * Sends a service call: an ordinary contract offer from one of the issuer's own houses, open
     * until the period ends (or for {@link #SERVICE_CALL_LEAD_DAYS} days to a suspended warrant).
     */
    private static void maybeSendServiceCall(CampaignState state, ArmorTradition issuer,
                                             WarrantObligationLedger.Term term, int day) {
        if (term.serviceDone() || term.status() == WarrantObligationLedger.Status.REVOKED) return;
        if (term.serviceCallId() >= 0 && outstanding(state, term.serviceCallId())) return;
        if (term.serviceCallId() >= 0 && term.serviceCallDeadline() >= day) return;
        boolean suspended = term.status() == WarrantObligationLedger.Status.SUSPENDED;
        int end = periodEnd(term);
        if (!suspended && day < end - SERVICE_CALL_LEAD_DAYS) return;
        int deadline = suspended ? day + SERVICE_CALL_LEAD_DAYS : end;

        int patronRow = issuerHouse(state, issuer.key);
        if (patronRow < 0) return;
        Random random = new Random(((long) day << 32) ^ state.houseId[patronRow] ^ issuer.key.hashCode());
        HouseRank rank = HouseRank.fromByte(state.houseRank[patronRow]);
        ContractOfferTemplate template = null;
        for (int roll = 0; roll < SERVICE_CALL_ROLLS && template == null; roll++) {
            template = ContractOfferTemplate.roll(rank, random);
        }
        if (template == null) return;
        long target = -1L;
        if (!template.type.isStationing()) {
            target = targetHouse(state, issuer.key, random);
            if (target < 0) return;
        }
        long id = state.addContract(state.houseId[patronRow], target, -1L, template.type,
                ContractState.OFFERED, day, -1, deadline, template.phasesTotal, -1,
                state.houseMarketId[patronRow], -1, template.payout, 0,
                template.salvageBaseline, template.salvageBaseline, (byte) 100);
        term.recordServiceCall(id, deadline);
        announce("warrantObligationServiceCall", issuer, deadline - day);
    }

    private static boolean outstanding(CampaignState state, long contractId) {
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractId[i] != contractId) continue;
            ContractState contract = ContractState.fromByte(state.contractState[i]);
            return contract == ContractState.OFFERED || contract == ContractState.ACTIVE
                    || contract == ContractState.IN_PROGRESS;
        }
        return false;
    }

    /** The issuer's most approachable active house (lowest tier, Tier 4 excluded), or -1. */
    private static int issuerHouse(CampaignState state, String issuerKey) {
        int best = -1;
        for (int i = 0; i < state.houseCount; i++) {
            if (!usable(state, i) || !issuerKey.equals(faction(state, i))) continue;
            if (best < 0 || state.houseRank[i] < state.houseRank[best]) best = i;
        }
        return best;
    }

    private static long targetHouse(CampaignState state, String issuerKey, Random random) {
        int candidates = 0;
        for (int i = 0; i < state.houseCount; i++) {
            if (usable(state, i) && !issuerKey.equals(faction(state, i))) candidates++;
        }
        if (candidates == 0) return -1L;
        int pick = random.nextInt(candidates);
        for (int i = 0; i < state.houseCount; i++) {
            if (usable(state, i) && !issuerKey.equals(faction(state, i)) && pick-- == 0) return state.houseId[i];
        }
        return -1L;
    }

    private static boolean usable(CampaignState state, int row) {
        return HouseRank.fromByte(state.houseRank[row]) != HouseRank.TIER_4
                && HouseStatus.fromByte(state.houseStatus[row]) == HouseStatus.ACTIVE;
    }

    private static String houseFaction(CampaignState state, long houseId) {
        if (houseId < 0) return null;
        int row = state.houseIndex(houseId);
        return row >= 0 ? faction(state, row) : null;
    }

    private static String faction(CampaignState state, int row) {
        return state.factionRegistry.get(state.houseFactionId[row]);
    }

    private static RepLevel liveRelationship(String factionId) {
        SectorAPI sector = Global.getSector();
        if (sector == null) return null;
        FactionAPI player = sector.getPlayerFaction();
        return player != null ? player.getRelationshipLevel(factionId) : null;
    }

    private static void announce(String key, ArmorTradition issuer, int days) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getCampaignUI() == null) return;
        sector.getCampaignUI().addMessage(Strings.format(key, issuer.displayName(), days));
    }
}
