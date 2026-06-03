package com.dillon.starsectormarines;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.HouseSeeder;
import com.dillon.starsectormarines.combathybrid.probe.CombatHybridCampaignPlugin;
import com.dillon.starsectormarines.combathybrid.probe.CombatHybridInputListener;
import com.dillon.starsectormarines.intel.BridgeIntel;
import com.dillon.starsectormarines.intel.CampaignDebugIntel;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Rank;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import org.apache.log4j.Logger;

public class StarsectorMarinesModPlugin extends BaseModPlugin {

    public static final String MOD_ID = "starsector_marines";

    private static final Logger LOG = Global.getLogger(StarsectorMarinesModPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        LOG.info("Starsector Marines: jar loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        LOG.info("Starsector Marines: game loaded (newGame=" + newGame + ")");
        ensureBridgeIntel();
        ensureMarineRoster();
        ensureCampaignState();
        if (DevConfig.CAMPAIGN_DEBUG_INTEL) {
            ensureCampaignDebugIntel();
        }
        if (DevConfig.S0_COMBAT_PROBE) {
            ensureCombatHybridProbe();
        }
        logRosterContents();
    }

    /**
     * Registers the S0 vanilla-combat-bridge probe: a {@link CombatHybridCampaignPlugin}
     * that can route {@code startBattle} to our minimal battle definition, and a
     * {@link CombatHybridInputListener} that arms it from a campaign-map hotkey.
     * Both are transient (not save-persisted), so we re-register each load and
     * de-dup defensively.
     */
    private static void ensureCombatHybridProbe() {
        SectorAPI sector = Global.getSector();

        sector.unregisterPlugin(CombatHybridCampaignPlugin.PLUGIN_ID);
        sector.registerPlugin(new CombatHybridCampaignPlugin());

        if (sector.getListenerManager().getListeners(CombatHybridInputListener.class).isEmpty()) {
            sector.getListenerManager().addListener(new CombatHybridInputListener(), true);
        }
        LOG.info("Starsector Marines: S0 combat-bridge probe registered (Ctrl+Shift+B on the campaign map)");
    }

    private static void ensureBridgeIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(BridgeIntel.class) != null) return;
        mgr.addIntel(new BridgeIntel(), true);
        LOG.info("Starsector Marines: Bridge intel registered");
    }

    private static void ensureMarineRoster() {
        SectorAPI sector = Global.getSector();
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script == null) {
            script = new MarineRosterScript();
            sector.addScript(script);
            LOG.info("Starsector Marines: MarineRosterScript registered");
        }
        MarineRoster roster = script.roster();
        if (roster.size() == 0) {
            float currentDay = sector.getClock().getDay();
            MarineCaptain starter = new MarineCaptain(
                    "Mira Hale",
                    "graphics/portraits/portrait_mercenary01.png",
                    Rank.SERGEANT,
                    currentDay);
            roster.add(starter);
            LOG.info("Starsector Marines: injected starter captain " + starter.name() + " [" + starter.id() + "]");
        }
    }

    private static void ensureCampaignState() {
        SectorAPI sector = Global.getSector();
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) {
            script = new CampaignStateScript();
            sector.addScript(script);
            LOG.info("Starsector Marines: CampaignStateScript registered");
        }
        CampaignState state = script.state();
        if (state.houseCount == 0) {
            HouseSeeder.seed(state);
        }
    }

    private static void ensureCampaignDebugIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(CampaignDebugIntel.class) != null) return;
        mgr.addIntel(new CampaignDebugIntel(), true);
        LOG.info("Starsector Marines: CampaignDebugIntel registered (dev)");
    }

    private static void logRosterContents() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script == null) {
            LOG.warn("Starsector Marines: no roster script after ensure; this should not happen");
            return;
        }
        MarineRoster roster = script.roster();
        LOG.info("Starsector Marines: roster has " + roster.size() + "/" + roster.capacity() + " captains");
        for (MarineCaptain c : roster.all()) {
            LOG.info("  - " + c.name()
                    + " [" + c.id() + "]"
                    + " rank=" + c.rank().displayName()
                    + " status=" + c.status()
                    + " xp=" + c.xp());
        }
    }
}
