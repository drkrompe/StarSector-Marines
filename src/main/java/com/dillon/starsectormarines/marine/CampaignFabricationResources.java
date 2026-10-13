package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.dillon.starsectormarines.campaign.start.WorkshopStores;
import com.dillon.starsectormarines.campaign.start.PreparationDepot.Purpose;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live player-fleet cargo plus any accessible prepaid depot and vanilla commodity presentation for the workshops. */
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


    @Override
    public int available(String commodityId) {
        return WorkshopStores.current(purpose).available(commodityId);
    }

    @Override
    public String commodityName(String commodityId) {
        return CampaignCommodityPresentation.INSTANCE.commodityName(commodityId);
    }

    @Override
    public String commodityIcon(String commodityId) {
        return CampaignCommodityPresentation.INSTANCE.commodityIcon(commodityId);
    }

    @Override
    public boolean spend(FabricationCost cost) {
        Map<String, Integer> bill = new LinkedHashMap<>();
        for (FabricationCost.Line line : cost.lines())
            bill.merge(line.commodityId(), line.quantity(), Integer::sum);
        return WorkshopStores.current(purpose).spend(bill);
    }
}
