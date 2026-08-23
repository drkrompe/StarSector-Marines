package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.EnumSet;

/**
 * Tick phase 2: weekly relationship interaction rolls.
 *
 * <p>Relationship interaction is intentionally an explicit no-op. The weekly
 * seam remains reserved for visibility-gated edges and interaction outcomes
 * described by
 * <code>roadmap/campaign/living-world/design/house-disposition.md</code>; no
 * relationship edges are created here yet.
 */
public final class RelationshipInteractionSystem implements CampaignSystem {

    @Override
    public String name() {
        return "RelationshipInteraction";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.RELATIONSHIPS, CampaignTable.CHAINS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (day % 7 != 0) return;
        // Pending: walk relationships[], roll interactions, mutate affinity,
        // possibly create autonomous chains.
    }
}
