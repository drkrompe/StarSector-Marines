package com.dillon.starsectormarines;

import com.dillon.starsectormarines.intel.BridgeIntel;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
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
    }

    private static void ensureBridgeIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(BridgeIntel.class) != null) return;
        mgr.addIntel(new BridgeIntel(), true);
        LOG.info("Starsector Marines: Bridge intel registered");
    }
}
