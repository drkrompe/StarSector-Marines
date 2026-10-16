package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.WarrantObligationLedger;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

/**
 * Grants each tradition's standing military warrant once the company has earned it:
 * the Prestige reputation band and close standing with that faction
 * ({@link MilitaryWarrants#standingWarrantDue}). This is the route that keeps every
 * Restricted template reachable, so the open routes could close without stranding
 * anything; authored warrants arrive earlier through their own story beats.
 *
 * <p>A grant opens every Restricted template the tradition builds that the company
 * does not already hold under some warrant, so a partial authored warrant is completed
 * by the standing one rather than duplicated.
 */
public final class StandingWarrantSystem implements CampaignSystem {

    private final Function<String, RepLevel> relationship;

    public StandingWarrantSystem() {
        this(StandingWarrantSystem::liveRelationship);
    }

    StandingWarrantSystem(Function<String, RepLevel> relationship) {
        this.relationship = relationship;
    }

    @Override
    public String name() {
        return "StandingWarrant";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.PLAYER_REP, CampaignTable.MILITARY_WARRANTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.MILITARY_WARRANTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (ArmorTradition issuer : MilitaryWarrants.issuers()) {
            if (!MilitaryWarrants.standingWarrantDue(state.playerMrbRep, relationship.apply(issuer.key))) {
                continue;
            }
            List<String> opened = MilitaryWarrants.grantStanding(state, issuer, day);
            if (!opened.isEmpty()) announce(issuer, "militaryWarrantStandingGranted");
            // A revoked warrant is replaced by a fresh standing grant once the rule is met again.
            WarrantObligationLedger.Term term = state.warrantObligations().term(issuer.key);
            if (term != null && term.status() == WarrantObligationLedger.Status.REVOKED) {
                term.regrant(day);
                announce(issuer, "warrantObligationRegranted");
            }
        }
    }

    private static RepLevel liveRelationship(String factionId) {
        SectorAPI sector = Global.getSector();
        if (sector == null) return null;
        FactionAPI player = sector.getPlayerFaction();
        return player != null ? player.getRelationshipLevel(factionId) : null;
    }

    private static void announce(ArmorTradition issuer, String key) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getCampaignUI() == null) return;
        sector.getCampaignUI().addMessage(Strings.format(key, issuer.displayName()));
    }
}
