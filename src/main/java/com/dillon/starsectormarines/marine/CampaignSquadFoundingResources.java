package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.dillon.starsectormarines.campaign.start.WorkshopStores;
import com.dillon.starsectormarines.campaign.start.PreparationDepot.Purpose;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live player-fleet cargo plus any accessible prepaid depot and vanilla commodity presentation for squad founding. */
public final class CampaignSquadFoundingResources implements SquadFoundingResources {
    private final Purpose purpose;

    public CampaignSquadFoundingResources() { this(Purpose.SQUAD); }
    private CampaignSquadFoundingResources(Purpose purpose) { this.purpose = purpose; }

    /** The whole consignment counted for a preview, spendable by nothing. */
    public static CampaignSquadFoundingResources forPreparation() {
        return new CampaignSquadFoundingResources(Purpose.PREFLIGHT);
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
    public boolean spend(SquadFoundingCost cost) {
        Map<String, Integer> bill = new LinkedHashMap<>();
        for (SquadFoundingCost.Line line : cost.lines())
            bill.merge(line.commodityId(), line.quantity(), Integer::sum);
        return WorkshopStores.current(purpose).spend(bill);
    }
}
