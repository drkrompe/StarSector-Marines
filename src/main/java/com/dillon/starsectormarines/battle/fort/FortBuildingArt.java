package com.dillon.starsectormarines.battle.fort;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What a fort building adds over the kit it is built from (`fort-works.md`): where the holder's banner stands, the blast
 * door laid over its main door, and when it counts as hurt. The building itself (walls, doors, roof) is an ordinary kit
 * building drawn in its lot's style. Authored on the structure that is the core of the building's aspect, so every fort
 * kind that houses an aspect draws it the same way.
 *
 * <p>The banner's coordinates are cells from the top-left of the building's floor in the fort's local frame, authored
 * with the door on the edge {@link #doorOnTop} names; the renderer turns them with the fort.
 *
 * <p>Pure data and pure state laws: no sprite is touched here.
 */
public final class FortBuildingArt {

    /** The three conditions a building is drawn in. */
    public enum State {
        /** Standing and sound. */
        INTACT,
        /** Standing but hurt. */
        DAMAGED,
        /** Wrecked: the roof is gone. */
        WRECKED
    }

    /** The blast door laid over the building's main door, intact and hurt. */
    public record BlastDoor(String sprite, String damaged) { }

    /** Where the holder's banner stands, in cells from the floor's top-left, and how tall it is. */
    public record Banner(float x, float y, float cells) { }

    /** A building is drawn damaged below this fraction of its core's durability unless its art says otherwise. */
    public static final float DEFAULT_DAMAGED_BELOW = 0.6f;

    private final Banner banner;
    private final BlastDoor blastDoor;
    private final float damagedBelow;
    private final boolean doorOnTop;

    private FortBuildingArt(Banner banner, BlastDoor blastDoor, float damagedBelow, boolean doorOnTop) {
        this.banner = banner;
        this.blastDoor = blastDoor;
        this.damagedBelow = damagedBelow;
        this.doorOnTop = doorOnTop;
    }

    public static FortBuildingArt parse(String structureId, JSONObject json) throws JSONException {
        Banner banner = null;
        JSONObject bannerJson = json.optJSONObject("banner");
        if (bannerJson != null) {
            float cells = finite(structureId, "banner cells", bannerJson.optDouble("cells", 1.4), 0f, 16f);
            banner = new Banner(finite(structureId, "banner x", bannerJson.getDouble("x"), -64f, 64f),
                    finite(structureId, "banner y", bannerJson.getDouble("y"), -64f, 64f), cells);
        }
        BlastDoor blastDoor = null;
        JSONObject doorJson = json.optJSONObject("blastDoor");
        if (doorJson != null) {
            blastDoor = new BlastDoor(require(structureId, doorJson, "sprite"), require(structureId, doorJson, "damaged"));
        }
        float below = finite(structureId, "damagedBelow",
                json.optDouble("damagedBelow", DEFAULT_DAMAGED_BELOW), 0f, 1f);
        String door = json.optString("door", "bottom").trim();
        if (!door.equals("top") && !door.equals("bottom")) {
            throw new JSONException("Structure '" + structureId + "' building art door must be 'top' or 'bottom'");
        }
        return new FortBuildingArt(banner, blastDoor, below, door.equals("top"));
    }

    private static String require(String structureId, JSONObject json, String key) throws JSONException {
        String value = json.optString(key, "").trim();
        if (value.isEmpty()) throw new JSONException("Structure '" + structureId + "' building art is missing '" + key + "'");
        return value;
    }

    private static float finite(String structureId, String what, double value, float min, float max)
            throws JSONException {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new JSONException("Structure '" + structureId + "' building art " + what + " out of range: " + value);
        }
        return (float) value;
    }

    /**
     * The condition a building is drawn in. A wrecked aspect is wrecked whatever its core's durability reads; a
     * standing one is damaged once its core has lost enough of its durability, and sound before that.
     *
     * @param wrecked            whether the aspect's core has been destroyed
     * @param durabilityFraction the core's remaining structure plus armour over its full amount, 0 to 1
     */
    public State stateOf(boolean wrecked, float durabilityFraction) {
        if (wrecked) return State.WRECKED;
        return durabilityFraction < damagedBelow ? State.DAMAGED : State.INTACT;
    }

    /** Where the holder's banner stands, or null for a building that flies none. */
    public Banner banner() {
        return banner;
    }

    /** Whether the banner is authored with the building's door on its top edge rather than its bottom edge. */
    public boolean doorOnTop() {
        return doorOnTop;
    }

    /**
     * Where a point given in cells from the top-left of the building floor falls on the plan's floor in the same terms:
     * unchanged, or through the centre when the authored frame is turned half a revolution to put its door where the
     * plan's is.
     */
    public static float[] registered(boolean halfTurned, float floorWidth, float floorHeight, float x, float y) {
        return halfTurned ? new float[]{floorWidth - x, floorHeight - y} : new float[]{x, y};
    }

    /** Whether the authored frame must be turned half a revolution to put its door where a plan's building has it. */
    public boolean needsHalfTurn(boolean planDoorOnTop) {
        return doorOnTop != planDoorOnTop;
    }

    /** The heavy door laid over the main door, or null for a building without one. */
    public BlastDoor blastDoor() {
        return blastDoor;
    }

    public float damagedBelow() {
        return damagedBelow;
    }

    /** Every sprite path this art names, so a catalog check can prove each one exists. */
    public List<String> spritePaths() {
        List<String> paths = new ArrayList<>();
        if (blastDoor != null) paths.addAll(List.of(blastDoor.sprite(), blastDoor.damaged()));
        return paths;
    }
}
