package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CivilizedPort;
import com.dillon.starsectormarines.campaign.start.WorkshopStores;
import com.dillon.starsectormarines.campaign.start.PreparationDepot.Purpose;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;

/** The player fleet's credits plus any accessible prepaid yard credit, and the port it is docked at, for the workshops. */
public final class CampaignFabricationResources implements FabricationResources {
    private final boolean workshopJob;
    private final Purpose purpose;
    public CampaignFabricationResources() { this(false, Purpose.GENERAL); }
    public CampaignFabricationResources(boolean workshopJob) { this(workshopJob, Purpose.GENERAL); }
    private CampaignFabricationResources(boolean workshopJob, Purpose purpose) {
        this.workshopJob = workshopJob; this.purpose = purpose;
    }
    public static CampaignFabricationResources forBoats() { return new CampaignFabricationResources(true, Purpose.BOAT); }
    public static CampaignFabricationResources forPreparation() { return new CampaignFabricationResources(false, Purpose.PREFLIGHT); }
    @Override public FabricationCost quote(FabricationCost base) {
        return WorkshopTooling.quote(base, workshopJob && CompanyWorkshopRestoration.toolingAboard());
    }

    @Override public Reclamation reclamation() {
        return workshopJob ? CompanyWorkshopRestoration.reclamationAboard() : Reclamation.NONE;
    }

    @Override public long credits() {
        return WorkshopStores.current(purpose).credits();
    }

    @Override public boolean atMarket() {
        return CivilizedPort.docked() != null;
    }

    @Override public boolean spend(FabricationCost cost) {
        return cost != null && WorkshopStores.current(purpose).spendCredits(cost.credits());
    }

    @Override public boolean credit(FabricationCost refund) {
        return refund != null && WorkshopStores.current(purpose).refund(refund.credits());
    }
}
