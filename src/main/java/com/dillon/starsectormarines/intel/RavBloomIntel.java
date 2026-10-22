package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.bloom.BloomEnd;
import com.dillon.starsectormarines.campaign.bloom.BloomStage;
import com.dillon.starsectormarines.campaign.bloom.BloomTuning;
import com.dillon.starsectormarines.campaign.bloom.RavBloom;
import com.dillon.starsectormarines.campaign.bloom.RavBloomLedger;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
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

/**
 * What the Sector says about the Rav Bloom: rumours of seepage, the eruption and its distress call,
 * the Hegemony's quarantine notice, a colony gone dark, and how each outbreak ended. Every line is
 * voiced by one speaker from {@code swarm-lore-guide.md}, and none names a maker or says the weapon
 * is gone. Read-only over the ledger; the distress call itself is the Distress Net's.
 */
public final class RavBloomIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_rav_bloom";
    private static final int HISTORY = 6;

    /** Stage changes worth a message in the campaign's corner. */
    public enum Notice { ERUPTED, DARK, STARVED, ENDED }

    /** Posts an update line for a bloom's stage change if the intel is installed. */
    public static void notice(RavBloom bloom, Notice notice) {
        if (Global.getSector() == null || bloom == null) return;
        IntelInfoPlugin intel = Global.getSector().getIntelManager().getFirstIntel(RavBloomIntel.class);
        if (!(intel instanceof RavBloomIntel)) return;
        ((RavBloomIntel) intel).sendUpdateIfPlayerHasIntel(headline(bloom, notice), false);
    }

    @Override
    protected String getName() {
        return Strings.get("ravBloomIntelTitle");
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        if (listInfoParam instanceof String) info.addPara((String) listInfoParam, 3f);
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
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(TAG);
        tags.add(CivilianRescueIntel.TAG);
        return tags;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public String getSortString() {
        return "AAB_" + getName();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        RavBloomLedger ledger = ledger();
        if (ledger == null) return null;
        for (RavBloom bloom : ledger.blooms()) {
            if (bloom.onTheSurface()) {
                MarketAPI market = market(bloom.marketId);
                if (market != null) return market.getPrimaryEntity();
            }
        }
        return null;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);
        ui.addSectionHeading(getName(), Color.WHITE, new Color(40, 40, 40), Alignment.LMID, 0f);
        RavBloomLedger ledger = ledger();
        List<RavBloom> running = new ArrayList<>();
        List<RavBloom> ended = new ArrayList<>();
        if (ledger != null) {
            for (RavBloom bloom : ledger.blooms()) (bloom.active() ? running : ended).add(bloom);
        }
        if (running.isEmpty()) {
            ui.addPara(Strings.get("ravBloomIntelEmpty"), 10f);
        } else {
            ui.addSectionHeading(Strings.get("ravBloomIntelRunning"), Alignment.LMID, 10f);
            for (RavBloom bloom : running) addRunning(ui, ledger, bloom);
        }
        if (!ended.isEmpty()) {
            ui.addSectionHeading(Strings.get("ravBloomIntelHistory"), Alignment.LMID, 10f);
            for (int i = ended.size() - 1; i >= Math.max(0, ended.size() - HISTORY); i--) {
                addEnded(ui, ended.get(i));
            }
        }
        panel.addUIElement(ui).inTL(0f, 0f);
    }

    private static void addRunning(TooltipMakerAPI ui, RavBloomLedger ledger, RavBloom bloom) {
        String world = name(bloom.marketId);
        if (bloom.stage() == BloomStage.STIRRING) {
            ui.addPara(Strings.format("ravBloomRumour", world), 10f);
            return;
        }
        RavBloom parent = bloom.parentId > 0L ? ledger.bloom(bloom.parentId) : null;
        ui.addPara(parent != null ? Strings.format("ravBloomSeeded", world, name(parent.marketId))
                : Strings.format("ravBloomErupted", world), 10f);
        if (bloom.quarantined()) {
            ui.addPara(Strings.format("ravBloomQuarantine", String.valueOf(1000 + bloom.id), world),
                    Color.LIGHT_GRAY, 4f);
        }
        if (bloom.stage() == BloomStage.DARK) {
            ui.addPara(darkLine(world), 6f);
            ui.addPara(Strings.get("ravBloomChurch"), Color.LIGHT_GRAY, 4f);
            ui.addPara(Strings.get("ravBloomPath"), Color.LIGHT_GRAY, 4f);
            if (eatsColonies()) ui.addPara(Strings.format("ravBloomPirates", world), Color.LIGHT_GRAY, 4f);
        } else if (bloom.stage() == BloomStage.STARVED) {
            ui.addPara(Strings.format("ravBloomStarved", world), 6f);
        }
        addFacts(ui, bloom);
    }

    private static void addEnded(TooltipMakerAPI ui, RavBloom bloom) {
        ui.addPara(Strings.format("ravBloomEnd" + bloom.end().name(), name(bloom.marketId)), 10f);
        addFacts(ui, bloom);
    }

    private static void addFacts(TooltipMakerAPI ui, RavBloom bloom) {
        if (bloom.evacuated() >= 0) {
            ui.addPara(Strings.format("ravBloomEvacuated", String.valueOf(bloom.evacuated()),
                    String.valueOf(bloom.atRisk())), Color.GRAY, 3f);
        }
        if (bloom.sizeLost() > 0) {
            ui.addPara(Strings.format("ravBloomLostSize", String.valueOf(bloom.sizeLost())), Color.GRAY, 3f);
        }
        int since = bloom.eruptDay() >= 0 ? bloom.eruptDay() : bloom.wakeDay;
        ui.addPara(Strings.format("ravBloomDayLine", String.valueOf(since)), Color.GRAY, 3f);
    }

    private static String headline(RavBloom bloom, Notice notice) {
        String world = name(bloom.marketId);
        switch (notice) {
            case ERUPTED: return Strings.format("ravBloomErupted", world);
            case DARK: return darkLine(world);
            case STARVED: return Strings.format("ravBloomStarved", world);
            default:
                return bloom.end() == BloomEnd.NONE ? darkLine(world)
                        : Strings.format("ravBloomEnd" + bloom.end().name(), world);
        }
    }

    /** At the first stage the garrison fights an unanswered bloom; at a harsher tier the colony goes quiet. */
    private static String darkLine(String world) {
        return eatsColonies() ? Strings.format("ravBloomDark", world) : Strings.format("ravBloomCordoned", world);
    }

    private static boolean eatsColonies() {
        return BloomTuning.live().severity.costsSize();
    }

    private static RavBloomLedger ledger() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state().ravBlooms() : null;
    }

    private static MarketAPI market(String marketId) {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return null;
        return Global.getSector().getEconomy().getMarket(marketId);
    }

    private static String name(String marketId) {
        MarketAPI market = market(marketId);
        return market != null && market.getName() != null ? market.getName() : marketId;
    }
}
