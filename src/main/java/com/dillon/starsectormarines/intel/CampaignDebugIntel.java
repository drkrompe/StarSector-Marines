package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseSeeder;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Dev-only intel screen for inspecting and mutating {@link CampaignState}.
 * Registration is gated by {@code DevConfig.CAMPAIGN_DEBUG_INTEL}; flip the
 * flag off (or strip this class) for prod builds.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Counters (house / stake / chain / rep totals).</li>
 *   <li>Bypass-gating toggle — lets {@code MissionGenerator} skip rep/rank
 *       checks for any-house-any-mission playtesting.</li>
 *   <li>Per-house row with rank, flavor, status, and a promote button.</li>
 *   <li>Reseed: wipes the houses table and re-seeds from current sector
 *       state. Useful when iterating on seeder logic.</li>
 * </ul>
 */
public class CampaignDebugIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_campaign_debug";
    private static final String TITLE = "Marines Debug";

    private static final String BTN_TOGGLE_BYPASS = "toggle-bypass";
    private static final String BTN_RESEED        = "reseed";
    private static final String BTN_PROMOTE       = "promote:";
    private static final String BTN_DEMOTE        = "demote:";

    @Override
    protected String getName() {
        return TITLE;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return TITLE;
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(TITLE, getTitleColor(mode), 0f);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(TAG);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return null;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public String getSortString() {
        return "ZZZ_" + TITLE; // sort to bottom of intel list
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);

        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) {
            ui.addPara("CampaignStateScript not registered — open a save first.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }
        CampaignState s = script.state();

        // ---- Counters ----
        ui.addSectionHeading("Campaign state", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 0f);
        ui.addPara("Houses: %s    Stakes: %s    Chains: %s    Player rep rows: %s    LastTickDay: %s",
                10f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(s.houseCount),
                String.valueOf(s.stakeCount),
                String.valueOf(s.chainCount),
                String.valueOf(s.repCount),
                String.valueOf(s.lastTickDay));
        ui.addPara("Faction registry: %s    Industry registry: %s    Market registry: %s",
                4f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(s.factionRegistry.size()),
                String.valueOf(s.industryRegistry.size()),
                String.valueOf(s.marketRegistry.size()));

        // ---- Toggles ----
        ui.addSectionHeading("Toggles", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 14f);
        String bypassLabel = "Bypass house gating: " + (s.debugBypassHouseGating ? "ON" : "OFF");
        ui.addButton(bypassLabel, BTN_TOGGLE_BYPASS, 280f, 24f, 8f);
        ui.addButton("Reseed houses (wipes existing)", BTN_RESEED, 280f, 24f, 8f);

        // ---- House list ----
        ui.addSectionHeading("Houses", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 14f);
        if (s.houseCount == 0) {
            ui.addPara("(none)", 8f);
        } else {
            int max = Math.min(s.houseCount, 200); // cap visible rows; full list is too tall
            for (int i = 0; i < max; i++) {
                renderHouseRow(ui, s, i);
            }
            if (s.houseCount > max) {
                ui.addPara("... %s more houses (truncated for UI)", 6f, Color.LIGHT_GRAY, Color.WHITE,
                        String.valueOf(s.houseCount - max));
            }
        }

        panel.addUIElement(ui).inTL(0f, 0f);
    }

    private void renderHouseRow(TooltipMakerAPI ui, CampaignState s, int i) {
        long id = s.houseId[i];
        HouseRank rank = HouseRank.fromByte(s.houseRank[i]);
        HouseFlavor flavor = HouseFlavor.fromByte(s.houseFlavor[i]);
        HouseStatus status = HouseStatus.fromByte(s.houseStatus[i]);
        String factionId = s.factionRegistry.get(s.houseFactionId[i]);
        String marketId  = s.marketRegistry.get(s.houseMarketId[i]);

        String name = s.houseDisplayName[i] != null ? s.houseDisplayName[i] : ("house#" + id);
        ui.addPara("[%s] %s — %s %s — faction=%s — market=%s — status=%s",
                8f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(id),
                name,
                rank.displayName(flavor),
                "(" + flavor.name().toLowerCase() + ")",
                factionId != null ? factionId : "?",
                marketId != null ? marketId : "?",
                status.name().toLowerCase());

        ui.addButton("Promote", BTN_PROMOTE + id, 90f, 20f, 2f);
        ui.addButton("Demote",  BTN_DEMOTE  + id, 90f, 20f, 2f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, com.fs.starfarer.api.ui.IntelUIAPI ui) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        CampaignState s = script.state();

        if (BTN_TOGGLE_BYPASS.equals(buttonId)) {
            s.debugBypassHouseGating = !s.debugBypassHouseGating;
        } else if (BTN_RESEED.equals(buttonId)) {
            wipeHouses(s);
            HouseSeeder.seed(s);
        } else if (buttonId instanceof String) {
            String b = (String) buttonId;
            if (b.startsWith(BTN_PROMOTE)) {
                long id = parseLong(b.substring(BTN_PROMOTE.length()), -1);
                int idx = s.houseIndex(id);
                if (idx >= 0) {
                    HouseRank current = HouseRank.fromByte(s.houseRank[idx]);
                    s.houseRank[idx] = current.next().toByte();
                    s.housePromotionProgress[idx] = 0;
                }
            } else if (b.startsWith(BTN_DEMOTE)) {
                long id = parseLong(b.substring(BTN_DEMOTE.length()), -1);
                int idx = s.houseIndex(id);
                if (idx >= 0) {
                    HouseRank current = HouseRank.fromByte(s.houseRank[idx]);
                    HouseRank prev = current.ordinal() > 0
                            ? HouseRank.values()[current.ordinal() - 1]
                            : current;
                    s.houseRank[idx] = prev.toByte();
                }
            }
        }

        ui.updateUIForItem(this);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        return BTN_RESEED.equals(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (BTN_RESEED.equals(buttonId)) {
            prompt.addPara("Wipe all houses and re-seed from current sector state?", 0f);
        }
    }

    @Override
    public String getConfirmText(Object buttonId) {
        return "Reseed";
    }

    @Override
    public String getCancelText(Object buttonId) {
        return "Cancel";
    }

    private static long parseLong(String s, long fallback) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Resets the houses table for reseed. Clears the id→index map alongside
     * the row count so reseed doesn't accumulate stale lookup entries. Does
     * NOT touch the string registries (xstream-persists their slots; clearing
     * would break stake/rep rows that still reference the old slots).
     */
    private static void wipeHouses(CampaignState s) {
        s.houseCount = 0;
        s.houseIndexById.clear();
        // Optional: also wipe stakes/chains/rep — for now keep them, they'll just point at dead ids.
        // Reseed will rebuild houses; any stale stake/rep rows can be cleaned up by a later pass.
    }
}
