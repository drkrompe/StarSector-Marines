package com.dillon.starsectormarines.campaign.nexerelin;

import com.fs.starfarer.api.Global;

/**
 * Whether Nexerelin is present. Every path into a class that names a Nexerelin type is reached only after this says
 * yes, so a game without Nexerelin never loads one ({@code nexerelin-nouns.md}).
 */
public final class Nexerelin {

    public static final String MOD_ID = "nexerelin";

    private Nexerelin() {}

    public static boolean isEnabled() {
        try {
            return Global.getSettings() != null
                    && Global.getSettings().getModManager().isModEnabled(MOD_ID);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
