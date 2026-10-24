package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
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
import java.util.function.Function;

/**
 * Keeps each held military warrant's obligations ({@code progression-nouns.md}, Kit and armory):
 * the period clock, loyalty, suspension and revocation.
 *
 * <p>Each obligation period asks for <em>loyalty</em>: no contract against the issuer, and the
 * relationship never below the warrant's floor. Broken loyalty suspends the warrant at once; a
 * period that ends with loyalty kept is honoured, and one honoured period restores a suspended
 * warrant. A hostile relationship revokes it. Service (a contract for the issuer, or an answered
 * service call) is no longer asked: the campaign offers no generic contracts
 * ({@code contracts-nouns.md}).
 * Nothing learned is revoked and no fitted suit is taken away: suspension only withholds new
 * acquisition and new fitting.
 */
public final class WarrantObligationSystem implements CampaignSystem {

    /** Campaign days in one obligation period. */
    public static final int PERIOD_DAYS = 90;

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
        return EnumSet.noneOf(CampaignTable.class);
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
                announce("warrantObligationSuspendedLoyalty", issuer, PERIOD_DAYS);
                if (!atFloor) term.breakLoyalty();
            } else if (day >= periodEnd(term)) {
                if (term.loyaltyBroken()) {
                    term.restartPeriod(day);
                } else {
                    boolean restored = term.status() == WarrantObligationLedger.Status.SUSPENDED;
                    term.honour(day);
                    if (restored) announce("warrantObligationRestored", issuer, PERIOD_DAYS);
                    honoured(state, issuer, day);
                }
            }
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

    /** Counts contracts taken against the issuer since the terms were stated. */
    private static void scanContracts(CampaignState state, String issuerKey, WarrantObligationLedger.Term term) {
        for (int i = 0; i < state.contractCount; i++) {
            ContractState contract = ContractState.fromByte(state.contractState[i]);
            if (state.contractAcceptedTick[i] < term.openedDay()) continue;
            boolean taken = contract == ContractState.ACTIVE || contract == ContractState.IN_PROGRESS
                    || contract == ContractState.COMPLETED;
            if (taken && issuerKey.equals(houseFaction(state, state.contractTargetHouseId[i]))) {
                term.breakLoyalty(state.contractId[i]);
            }
        }
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
