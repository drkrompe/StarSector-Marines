package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.HegemonyAuxiliaryWarrant;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.EnumSet;
import java.util.function.Function;

/**
 * Puts the Hegemony auxiliary warrant's terms in the company's correspondence once
 * *A Small War* has settled and the Hegemony stands at the licence threshold
 * ({@link HegemonyAuxiliaryWarrant#offerDue}). The offer is a record and a message, never a
 * grant: the company answers it at Company HQ.
 */
public final class AuxiliaryWarrantOfferSystem implements CampaignSystem {

    private final Function<String, RepLevel> relationship;

    public AuxiliaryWarrantOfferSystem() {
        this(AuxiliaryWarrantOfferSystem::liveRelationship);
    }

    AuxiliaryWarrantOfferSystem(Function<String, RepLevel> relationship) {
        this.relationship = relationship;
    }

    @Override
    public String name() {
        return "AuxiliaryWarrantOffer";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.PLAYER_REP);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.noneOf(CampaignTable.class);
    }

    @Override
    public void tick(CampaignState state, int day) {
        boolean settled = state.agreusArc != null && state.agreusArc.smallWarSettled();
        if (!HegemonyAuxiliaryWarrant.offerDue(settled,
                relationship.apply(ArmorTradition.HEGEMONY.key), state.auxiliaryWarrant())) {
            return;
        }
        if (HegemonyAuxiliaryWarrant.offer(state, day)) announce();
    }

    private static RepLevel liveRelationship(String factionId) {
        SectorAPI sector = Global.getSector();
        if (sector == null) return null;
        FactionAPI player = sector.getPlayerFaction();
        return player != null ? player.getRelationshipLevel(factionId) : null;
    }

    private static void announce() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getCampaignUI() == null) return;
        sector.getCampaignUI().addMessage(Strings.get("auxiliaryWarrantOffered"));
    }
}
