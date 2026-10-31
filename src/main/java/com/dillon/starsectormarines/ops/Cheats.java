package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.fs.starfarer.api.Global;

import java.util.Map;

/**
 * The mission board's Debug key: the alpha's cheats, open to every player. While it is on, the
 * board lists the debug missions (every mission, with sandbox forces, loadouts and settings that
 * never touch the company), Company HQ shows its debug button, and a battle's debug panel offers
 * Win to skip the mission. Saved with the game; a dev build starts with it on.
 */
public final class Cheats {
    private static final String KEY = "marines_cheats";

    private Cheats() { }

    public static boolean enabled() {
        Map<String, Object> data = persistentData();
        Object stored = data == null ? null : data.get(KEY);
        return stored instanceof Boolean on ? on : DevConfig.DEV_MODE;
    }

    public static void setEnabled(boolean enabled) {
        Map<String, Object> data = persistentData();
        if (data != null) data.put(KEY, enabled);
    }

    /**
     * Whether Win can skip {@code mission}. A chapter whose arc checks its victory against what the
     * battle recorded (cargo delivered, a post held) would refuse a bare win and not advance, so those
     * fights must be played.
     */
    public static boolean canWin(Mission mission) {
        return mission != null && !AgreusArcCampaign.isBattle(mission)
                && !ShepherdRecoveryCampaign.isBattle(mission) && !DistrictEscortCampaign.isBattle(mission);
    }

    private static Map<String, Object> persistentData() {
        return Global.getSector() == null ? null : Global.getSector().getPersistentData();
    }
}
