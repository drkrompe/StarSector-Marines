package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.dillon.starsectormarines.campaign.start.WorkshopStores;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live player-fleet cargo plus any accessible prepaid depot and vanilla commodity presentation for squad founding. */
public final class CampaignSquadFoundingResources implements SquadFoundingResources {

    @Override
    public int available(String commodityId) {
        return WorkshopStores.current().available(commodityId);
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
        return WorkshopStores.current().spend(bill);
    }
}
