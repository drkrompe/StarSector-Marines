package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.campaign.backend.CampaignBackend;

import java.util.Map;

/**
 * The Settings page's 3D walls switch: whether buildings and freestanding walls stand up in the battle view's pinhole
 * camera (their faces turned to the view under lifted roofs, {@link BuildingFaces}, {@link FreestandingWallFaces}) or
 * lie flat, roofs over their footprints, as they were drawn before the faces. Drawing only: walls, cover, sight and
 * damage are the same either way, and a flat view still shows holed and fallen roofs, razed buildings and openings.
 * Saved with the game; on until the player turns it off, and on with no campaign running. Read every frame, so it
 * applies at once.
 *
 * <p>Owner: "We should have this '3d' wall effect be in the settings menu to toggle off. I feel like this might be an
 * acquired taste."
 */
public final class WallDepth {
    private static final String OFF_KEY = "marines_wall_depth_off";
    /** A look shot's choice, standing in for the save where no campaign runs; null reads the save. */
    private static volatile Boolean look;

    private WallDepth() { }

    public static boolean enabled() {
        Boolean forced = look;
        return forced != null ? forced : enabled(CampaignBackend.savedData());
    }

    public static void setEnabled(boolean enabled) {
        setEnabled(CampaignBackend.savedData(), enabled);
    }

    /** Sets the switch for a look shot with no campaign behind it; null hands it back to the save. */
    static void lookAs(Boolean enabled) {
        look = enabled;
    }

    static boolean enabled(Map<String, Object> saved) {
        return saved == null || !Boolean.TRUE.equals(saved.get(OFF_KEY));
    }

    static void setEnabled(Map<String, Object> saved, boolean enabled) {
        if (saved != null) saved.put(OFF_KEY, !enabled);
    }
}
