package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** Permanent yard work; the variant is the sole authority for the conversion. */
public final class MercenaryCompanyConversion extends BaseHullMod {
    public static final String ID = "sm_mercenary_company_conversion";
    public static final int EXTRA_BERTHS = 18;
    public static final int CARGO_COST = 25;
    public static final int CREDIT_COST = 5000;

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
    }

    @Override public boolean isApplicableToShip(ShipAPI ship) { return false; }
    @Override public boolean showInRefitScreenModPickerFor(ShipAPI ship) { return false; }
    @Override public String getUnapplicableReason(ShipAPI ship) {
        return "Permanent yard conversion. Arrange installation through Mercenary conversion at a planetary spaceport.";
    }
}
