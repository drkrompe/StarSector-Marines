package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.warship.VanillaData;
import com.dillon.starsectormarines.battle.warship.WarshipScale;

/**
 * The picture a tender's drones wear: the hull sprite of the drone the Shepherd's
 * own built-in wing launches in fleet combat (the Borer), drawn at the same
 * hull scale as every other overhead ship, so a drone stands to its tender as it
 * does in the player's fleet fights.
 *
 * <p>The sprite is read from the drone hull's spec through {@link VanillaData}, so
 * a mod's re-skinned Borer is a re-skinned tender drone; the shipped path is the
 * fallback when the spec cannot be read.
 */
public final class TenderDroneArt {

    /** The drone hull the Shepherd's {@code borer_wing} launches. */
    public static final String DRONE_HULL_ID = "drone_borer";
    /** The vanilla Borer sprite, used when its hull spec cannot be read. */
    public static final String FALLBACK_SPRITE = "graphics/ships/drones/drone_borer.png";

    private static volatile String resolved;

    private TenderDroneArt() {}

    /** The Borer's sprite path, read once from its hull spec. */
    public static String spritePath() {
        String path = resolved;
        if (path != null) return path;
        try {
            String name = VanillaData.json("data/hulls/" + DRONE_HULL_ID + ".ship").optString("spriteName", "");
            if (!name.isEmpty()) {
                resolved = name;
                return name;
            }
        } catch (Exception e) {
            // A spec that cannot be read leaves the shipped sprite; not memoised, so a later install is honoured.
        }
        return FALLBACK_SPRITE;
    }

    /** Cells a sprite {@code pixels} tall stands, at the scale every overhead hull is drawn. */
    public static float cells(float pixels) {
        return pixels * WarshipScale.cells(1f);
    }
}
