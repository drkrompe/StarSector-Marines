package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.EnumSet;

/**
 * Tick phase 2: weekly relationship interaction rolls.
 *
 * <p>Per <code>mechanics.md</code>: every 7 days, walk the
 * {@code relationships[]} edges and roll interactions that shift affinity
 * (and may trigger chain creation).
 *
 * <p>Stub — no edges exist yet because visibility-gated edge creation
 * hasn't landed.
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
