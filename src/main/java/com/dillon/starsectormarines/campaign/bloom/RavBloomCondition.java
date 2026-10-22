package com.dillon.starsectormarines.campaign.bloom;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The Rav Bloom market condition: stability and accessibility by the bloom's stage, read from the
 * ledger at every apply. A condition left behind with no bloom running under it applies nothing.
 */
public final class RavBloomCondition extends BaseMarketConditionPlugin {

    public static final String ID = "smk_rav_bloom";

    /** Stability and accessibility a bloom costs its market at a stage. */
    public static int stabilityPenalty(BloomStage stage) {
        switch (stage) {
            case ERUPTED: return 2;
            case DARK: return 5;
            case STARVED: return 3;
            default: return 0;
        }
    }

    public static float accessibilityPenalty(BloomStage stage, boolean quarantined) {
        float base;
        switch (stage) {
            case ERUPTED: base = 0.10f; break;
            case DARK: base = 0.30f; break;
            case STARVED: base = 0.15f; break;
            default: return 0f;
        }
        return base + (quarantined ? 0.10f : 0f);
    }

    @Override
    public void apply(String id) {
        RavBloom bloom = bloom();
        if (bloom == null || !bloom.onTheSurface()) return;
        String reason = Strings.get("ravBloomConditionName");
        int stability = stabilityPenalty(bloom.stage());
        float access = accessibilityPenalty(bloom.stage(), bloom.quarantined());
        if (stability > 0) market.getStability().modifyFlat(id, -stability, reason);
        if (access > 0f) market.getAccessibilityMod().modifyFlat(id, -access, reason);
    }

    @Override
    public void unapply(String id) {
        market.getStability().unmodifyFlat(id);
        market.getAccessibilityMod().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        RavBloom bloom = bloom();
        if (bloom == null || !bloom.onTheSurface()) return;
        tooltip.addPara(Strings.get("ravBloomStage_" + bloom.stage().name()), 10f);
        tooltip.addPara(Strings.get("ravBloomConditionStability"), 6f, Misc.getHighlightColor(),
                "-" + stabilityPenalty(bloom.stage()));
        tooltip.addPara(Strings.get("ravBloomConditionAccess"), 6f, Misc.getHighlightColor(),
                "-" + Math.round(accessibilityPenalty(bloom.stage(), bloom.quarantined()) * 100f) + "%");
        if (bloom.quarantined()) tooltip.addPara(Strings.get("ravBloomConditionQuarantine"), 6f);
    }

    private RavBloom bloom() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null || market == null) return null;
        return script.state().ravBlooms().activeAt(market.getId());
    }
}
