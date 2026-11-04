package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CivilizedPort;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;
import com.fs.starfarer.api.campaign.CargoAPI;

/** The player fleet's credits, and the port it is docked at, for the workshops. */
public final class CampaignFabricationResources implements FabricationResources {
    private final boolean workshopJob;
    public CampaignFabricationResources() { this(false); }
    public CampaignFabricationResources(boolean workshopJob) { this.workshopJob = workshopJob; }
    @Override public FabricationCost quote(FabricationCost base) {
        return WorkshopTooling.quote(base, workshopJob && CompanyWorkshopRestoration.toolingAboard());
    }

    @Override public Reclamation reclamation() {
        return workshopJob ? CompanyWorkshopRestoration.reclamationAboard() : Reclamation.NONE;
    }

    @Override public long credits() {
        CargoAPI cargo = cargo();
        return cargo == null || cargo.getCredits() == null ? 0 : Math.max(0, (long) Math.floor(cargo.getCredits().get()));
    }

    @Override public boolean atMarket() {
        return CivilizedPort.docked() != null;
    }

    @Override public boolean spend(FabricationCost cost) {
        if (cost == null || credits() < cost.credits()) return false;
        cargo().getCredits().subtract(cost.credits());
        return true;
    }

    @Override public boolean credit(FabricationCost refund) {
        CargoAPI cargo = cargo();
        if (refund == null || cargo == null || cargo.getCredits() == null) return false;
        cargo.getCredits().add(refund.credits());
        return true;
    }

    private static CargoAPI cargo() {
        return CampaignBackend.playerFleet() == null ? null : CampaignBackend.playerFleet().getCargo();
    }
}
