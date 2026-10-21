package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitText;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/** Permanent yard work; the variant is the sole authority for the conversion. */
public final class MercenaryCompanyConversion extends BaseHullMod {
    public static final String ID = "sm_mercenary_company_conversion";
    public static final int EXTRA_BERTHS = 18;
    public static final int CARGO_COST = 25;
    /** Nessa's itemised estimate: materials 3,300, yard time 1,562, pressure test {@link #PRESSURE_TEST}. */
    public static final int CREDIT_COST = 4986;
    /** The estimate's pressure-test line, waived when Castle's founding reserve pays (see {@link FoundingReserve}). */
    public static final int PRESSURE_TEST = 124;

    public static boolean compatible(FleetMemberAPI member) {
        return member != null && member.getHullSpec() != null
                && "cerberus".equals(member.getHullSpec().getBaseHullId());
    }

    public static boolean installed(FleetMemberAPI member) {
        return compatible(member) && member.getVariant() != null && member.getVariant().hasHullMod(ID);
    }

    public static boolean planetaryYard(MarketAPI market) {
        return market != null && market.getPlanetEntity() != null
                && !market.isPlanetConditionMarketOnly()
                && market.getIndustries().stream().anyMatch(i -> i.isFunctional()
                && i.getSpec().hasTag("spaceport"));
    }

    @Override public void applyEffectsBeforeShipCreation(ShipAPI.HullSize size, MutableShipStatsAPI stats, String id) {
        if (stats.getVariant() == null || !"cerberus".equals(stats.getVariant().getHullSpec().getBaseHullId())) return;
        stats.getMaxCrewMod().modifyFlat(id, EXTRA_BERTHS);
        stats.getCargoMod().modifyFlat(id, -CARGO_COST);
        applyRefits(ShipRefits.condition(stats.getFleetMember()), stats, id);
    }

    /**
     * The founding hull's combat refit tracks, as base-game stats so the refit
     * screen shows them and every fight she is in uses them. Every other hull
     * is neutral and receives nothing here.
     */
    static void applyRefits(ShipCondition refit, MutableShipStatsAPI stats, String id) {
        if (!refit.applies()) return;
        stats.getHullBonus().modifyMult(id, refit.hullMult());
        stats.getArmorBonus().modifyMult(id, refit.armorMult());
        stats.getFluxCapacity().modifyMult(id, refit.fluxCapacityMult());
        stats.getFluxDissipation().modifyMult(id, refit.fluxDissipationMult());
        stats.getSensorStrength().modifyMult(id, refit.sensorStrengthMult());
        stats.getSensorProfile().modifyMult(id, refit.sensorProfileMult());
        stats.getMaxSpeed().modifyMult(id, refit.speedMult());
        stats.getAcceleration().modifyMult(id, refit.accelerationMult());
        stats.getDeceleration().modifyMult(id, refit.accelerationMult());
        stats.getMaxBurnLevel().modifyFlat(id, refit.burnDelta());
    }

    @Override public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize size, ShipAPI ship,
                                                    float width, boolean isForModSpec) {
        var refit = ship == null ? ShipCondition.NONE : ShipRefits.condition(ship.getFleetMember());
        if (!refit.applies()) return;
        tooltip.addSectionHeading(Strings.get("openingRefitGroupCOMBAT"), Alignment.MID, 10f);
        for (RefitTrack track : RefitTrack.values()) {
            if (track.group != RefitTrack.Group.COMBAT) continue;
            RefitBand band = refit.band(track);
            tooltip.addPara(Strings.format("openingRefitTooltipRow", RefitText.track(track),
                    RefitText.tier(track, band), RefitText.effect(track, band)), 4f);
        }
    }

    @Override public boolean isApplicableToShip(ShipAPI ship) { return false; }
    @Override public boolean showInRefitScreenModPickerFor(ShipAPI ship) { return false; }
    @Override public String getUnapplicableReason(ShipAPI ship) {
        return Strings.get("openingMercenaryCompanyConversionPermanentYardConversionArrangeInstallationThroughMercenary");
    }
}
