package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.KingmakerTestament;
import com.dillon.starsectormarines.campaign.KingmakerTestamentEditor;
import com.dillon.starsectormarines.campaign.KingmakerTestamentState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reconstructible historical correspondence for kingmaker-capstone testimony. */
public final class LastTestamentIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_last_testament";
    private static final String TITLE = "Last Testament";

    record Rendered(int row, long testamentId, String deposedName,
                    KingmakerTestamentEditor.Draft draft) {}

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
        CampaignState state = state();
        List<Rendered> rendered = renderable(state, runtimeNames(state));
        if (rendered.isEmpty()) return null;
        MarketAPI market = market(state,
                state.kingmakerTestamentMarketId[rendered.get(0).row()]);
        return market != null ? market.getPrimaryEntity() : null;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public boolean isHidden() {
        return shouldHide(state());
    }

    @Override
    public String getSortString() {
        return "AAD_" + TITLE;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width,
                                       float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);
        CampaignState state = state();
        ui.addSectionHeading(TITLE, Color.WHITE, new Color(40, 40, 40),
                Alignment.LMID, 0f);
        if (state == null) {
            ui.addPara("The archive receiver is offline.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        List<Rendered> rendered = renderable(state, runtimeNames(state));
        if (rendered.isEmpty()) {
            ui.addPara("No authenticated testament can be reconstructed.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        ui.addPara("An authenticated dead-drop bears the seal of a displaced "
                + "ruler.", 10f);
        for (Rendered entry : rendered) {
            ui.addSectionHeading("Testament of " + entry.deposedName(),
                    Color.WHITE, new Color(50, 42, 42), Alignment.LMID, 10f);
            ui.addPara("Commander—", 8f);
            ui.addPara(entry.draft().accusation(), 6f,
                    Color.LIGHT_GRAY, Color.WHITE);
            for (KingmakerTestamentEditor.Witness witness
                    : entry.draft().witnesses()) {
                ui.addPara(witness.text(), 6f, Color.LIGHT_GRAY, Color.WHITE);
            }
            ui.addPara(entry.draft().verdict(), 10f,
                    Color.LIGHT_GRAY, Color.WHITE);
        }
        revealRendered(state, rendered);
        panel.addUIElement(ui).inTL(0f, 0f);
    }

    static boolean shouldHide(CampaignState state) {
        if (state == null) return true;
        for (int row = 0; row < state.kingmakerTestamentCount; row++) {
            KingmakerTestamentState status = KingmakerTestamentState.fromByte(
                    state.kingmakerTestamentState[row]);
            if (state.kingmakerTestamentId[row] > 0L
                    && (status == KingmakerTestamentState.SEALED
                        || status == KingmakerTestamentState.REVEALED)) {
                return false;
            }
        }
        return true;
    }

    static List<Rendered> renderable(CampaignState state,
                                     KingmakerTestamentEditor.Names names) {
        List<Rendered> result = new ArrayList<>();
        if (state == null || names == null) return result;
        for (int row = 0; row < state.kingmakerTestamentCount; row++) {
            final String deposedName;
            try {
                deposedName = clean(names.house(
                        state.kingmakerTestamentDeposedHouseId[row]));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (deposedName == null) continue;
            KingmakerTestamentEditor.Draft draft =
                    KingmakerTestamentEditor.edit(state, row, names)
                            .orElse(null);
            if (draft != null) {
                result.add(new Rendered(row, state.kingmakerTestamentId[row],
                        deposedName, draft));
            }
        }
        result.sort((left, right) -> {
            int byDay = Integer.compare(
                    state.kingmakerTestamentSealedTick[right.row()],
                    state.kingmakerTestamentSealedTick[left.row()]);
            if (byDay != 0) return byDay;
            return Long.compare(right.testamentId(), left.testamentId());
        });
        return result;
    }

    static void revealRendered(CampaignState state, List<Rendered> rendered) {
        if (state == null || rendered == null) return;
        for (Rendered entry : rendered) {
            if (entry != null) {
                KingmakerTestament.reveal(state, entry.testamentId());
            }
        }
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static KingmakerTestamentEditor.Names runtimeNames(
            CampaignState state) {
        if (state == null) return null;
        return new KingmakerTestamentEditor.Names() {
            @Override
            public String house(long houseId) {
                int row = state.houseIndex(houseId);
                return row >= 0 ? state.houseDisplayName[row] : null;
            }

            @Override
            public String market(int marketRegistryId) {
                MarketAPI market = LastTestamentIntel.market(
                        state, marketRegistryId);
                return market != null ? market.getName() : null;
            }
        };
    }

    private static MarketAPI market(CampaignState state, int marketRegistryId) {
        if (state == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) {
            return null;
        }
        String marketId = state.marketRegistry.get(marketRegistryId);
        return marketId != null
                ? Global.getSector().getEconomy().getMarket(marketId) : null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
