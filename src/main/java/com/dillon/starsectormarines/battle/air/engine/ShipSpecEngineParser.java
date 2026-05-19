package com.dillon.starsectormarines.battle.air.engine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses the {@code engineSlots} array out of a vanilla {@code .ship} hull
 * spec and converts each slot from vanilla's ship-local frame into the
 * shuttle-local frame this mod's renderer uses.
 *
 * <h2>Frame conversion</h2>
 * <p>Vanilla ships are drawn nose-up in their sprite image and their
 * ship-local frame has {@code +X = nose / forward}, {@code +Y = port}
 * (left when facing forward). Slot positions in {@code .ship} files are in
 * <em>pixels</em>, relative to the sprite's pixel center. Vanilla angles use
 * {@code 0° = +X (forward)}, CCW-positive.
 *
 * <p>This mod's shuttle frame has {@code +Y = nose / forward},
 * {@code +X = starboard} (matches the
 * {@link com.dillon.starsectormarines.battle.air.TurretMount} convention),
 * positions in <em>cells</em>, and the sprite-angle convention
 * {@code 0° = +Y (forward)}, CCW-positive. Both conventions agree
 * numerically on angle — {@code 0° = forward} in each frame — so the
 * angle value transfers through unchanged.
 *
 * <p>The position transform is the perpendicular axis swap with a
 * port-to-starboard sign flip:
 * <pre>
 *   our.x =  -vy_pixels / pxPerCell   // port-positive (+Y) → starboard-negative (-X)
 *   our.y =   vx_pixels / pxPerCell   // forward (+X) → forward (+Y)
 * </pre>
 *
 * <p>{@code pxPerCell} comes from the spec's {@code height} property —
 * vanilla's sprite "height" is the pixel extent along the ship's
 * forward (+X) axis since ships are drawn nose-up. Combined with the
 * {@link com.dillon.starsectormarines.battle.air.ShuttleType#visualLengthCells}
 * we want this hull to render at, that fixes the pixel-to-cell scale.
 *
 * <h2>Sprite anchor</h2>
 * <p>Slot positions in {@code .ship} files are stored relative to the
 * sprite's pixel center, NOT to vanilla's {@code center} property. We
 * tried adding {@code (spec.center - pixelCenter)} compensation when
 * dagger looked slightly off, but it scrambled every other hull —
 * Valkyrie's {@code center=[42,113]} on an 84×264 sprite produced a
 * 19-pixel shift, which is the opposite of what we want given how
 * {@link com.fs.starfarer.api.graphics.SpriteAPI#renderAtCenter} anchors
 * our hull sprites. The renderer anchors at pixel center; slot positions
 * are expressed in that frame; no compensation needed.
 */
public final class ShipSpecEngineParser {

    private ShipSpecEngineParser() {}

    /**
     * Parses {@code engineSlots} out of the given .ship JSON document and
     * returns each slot in our shuttle-local frame.
     *
     * @param shipJson           raw {@code .ship} file contents (vanilla
     *                           ships are clean JSON; if a future hull
     *                           ships with comments we'll add stripping here)
     * @param visualLengthCells  the shuttle's rendered length in cells; pairs
     *                           with the spec's {@code height} pixel value to
     *                           fix the px-to-cell ratio
     * @return one entry per engine slot, in spec order; empty array if the
     *         spec has no {@code engineSlots}
     */
    public static EngineSlotData[] parse(String shipJson, float visualLengthCells) throws JSONException {
        return parse(new JSONObject(shipJson), visualLengthCells);
    }

    /**
     * Same as {@link #parse(String, float)} but operates on an already-parsed
     * {@link JSONObject}. The in-game path uses this with
     * {@code Global.getSettings().loadJSON("data/hulls/<id>.ship")} so we
     * don't re-stringify a JSON document the game already parsed.
     */
    public static EngineSlotData[] parse(JSONObject root, float visualLengthCells) throws JSONException {
        JSONArray slots = root.optJSONArray("engineSlots");
        if (slots == null || slots.length() == 0) {
            return new EngineSlotData[0];
        }

        // Vanilla's "height" is the pixel extent along the ship's +X (forward)
        // axis. We anchor pxPerCell to that so all per-slot pixel measures
        // (location, length, width, contrailSize) convert uniformly.
        float spriteHeightPx = (float) root.optDouble("height", 0.0);
        if (spriteHeightPx <= 0f) {
            throw new IllegalArgumentException("ship spec missing/invalid 'height' — "
                    + "needed to derive pxPerCell for engine slot transform");
        }
        float pxPerCell = spriteHeightPx / visualLengthCells;

        EngineSlotData[] out = new EngineSlotData[slots.length()];
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.getJSONObject(i);
            JSONArray loc = slot.getJSONArray("location");
            float vx = (float) loc.getDouble(0);
            float vy = (float) loc.getDouble(1);
            float angle  = (float) slot.optDouble("angle",        180.0);
            float length = (float) slot.optDouble("length",         8.0);
            float width  = (float) slot.optDouble("width",          4.0);
            float contrail = (float) slot.optDouble("contrailSize", 0.0);
            String style = slot.optString("style", "MIDLINE");

            // Frame transform — see class javadoc. Angle is conserved across
            // the frame swap because both vanilla and ours define angle as
            // CCW-positive from "ship-forward".
            float ourX = -vy / pxPerCell;
            float ourY =  vx / pxPerCell;

            out[i] = new EngineSlotData(
                    ourX, ourY, angle,
                    length / pxPerCell,
                    width / pxPerCell,
                    contrail / pxPerCell,
                    style);
        }
        return out;
    }
}
