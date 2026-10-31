package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.fs.starfarer.api.Global;

import java.util.Map;

/**
 * The mission board's Debug key: the alpha's cheats, open to every player. While it is on, the
 * board lists the debug missions (every mission, with sandbox forces, loadouts and settings that
 * never touch the company), Company HQ shows its debug button, and a battle's debug panel offers
 * Win, which ends any battle as an outright company victory: every story record reports each
 * objective met and nobody lost ({@code BattleSimulation#isForcedVictory}), so the arc settles it
 * as a full success. Saved with the game; a dev build starts with it on.
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

    private static Map<String, Object> persistentData() {
        return Global.getSector() == null ? null : Global.getSector().getPersistentData();
    }
}
