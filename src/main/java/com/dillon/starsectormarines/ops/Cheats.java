package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.fs.starfarer.api.Global;

import java.util.Map;

/**
 * The mission board's CHEAT key: the alpha's cheats, open to every player. While it is on, the
 * board lists the debug missions (every mission, with sandbox forces, loadouts and settings that
 * never touch the company), Company HQ shows its debug button, and a battle's CHEAT panel offers
 * only Win, which ends any battle as an outright company victory: every story record reports each
 * objective met and nobody lost ({@code BattleSimulation#isForcedVictory}), so the arc settles it
 * as a full success. The battle's diagnostic rows stay behind {@code DevConfig.DEV_MODE}. Saved
 * with the game; a dev build starts with it on.
 */
public final class Cheats {
    private static final String KEY = "marines_cheats", FREE_LICENSES_KEY = "marines_cheat_free_licenses";

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

    /** The store's "[Cheat] Free Licenses" key: every store purchase costs nothing. Only while Debug is on. */
    public static boolean freeLicenses() {
        Map<String, Object> data = persistentData();
        return enabled() && data != null && Boolean.TRUE.equals(data.get(FREE_LICENSES_KEY));
    }

    public static void setFreeLicenses(boolean free) {
        Map<String, Object> data = persistentData();
        if (data != null) data.put(FREE_LICENSES_KEY, free);
    }

    private static Map<String, Object> persistentData() {
        return Global.getSector() == null ? null : Global.getSector().getPersistentData();
    }
}
