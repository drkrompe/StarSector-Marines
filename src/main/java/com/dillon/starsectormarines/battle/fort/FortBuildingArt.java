package com.dillon.starsectormarines.battle.fort;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How one kind of fort building is drawn, by function and by state: the roof art over the building's interior floor,
 * its damaged and destroyed pictures, a lights overlay, the parts that move, and where the holder's banner stands.
 * Authored on the structure that is the core of the building's aspect, so every fort kind that houses an aspect draws it
 * the same way and a new kind of building adds data, not code.
 *
 * <p>Every picture is authored for the building's interior rectangle in the fort's local frame and shares one
 * registration, so a change of state swaps the picture and moves nothing. Coordinates of rotors and the banner are
 * cells from that rectangle's top-left, in the same local frame; the renderer turns the lot with the fort.
 *
 * <p>Pure data and pure state laws: no sprite is touched here.
 */
public final class FortBuildingArt {

    /** The three conditions a building is drawn in. */
    public enum State {
        /** Standing and sound. */
        INTACT,
        /** Standing but hurt: torn roof, scorch, slower machinery. */
        DAMAGED,
        /** Wrecked: the roof is gone and what remains is a low wreck over the floor. */
        WRECKED
    }

    /** A part that turns on the roof. {@code damagedSpin} scales its speed while the building is damaged. */
    public record Rotor(String spritePath, float x, float y, float cells, float spinDegPerSec, float damagedSpin) { }

    /** A glow laid over the roof that pulses, and is dark while the building is not running. */
    public record Lights(String spritePath, float pulseHz, float pulseDepth) { }

    /** Where the holder's banner stands and how big it is, in cells. */
    public record Banner(float x, float y, float cells) { }

    /** Fraction of full durability below which a standing building is drawn damaged. */
    public static final float DEFAULT_DAMAGED_BELOW = 0.6f;

    private final String roof;
    private final String damaged;
    private final String wreck;
    private final Lights lights;
    private final List<Rotor> rotors;
    private final Banner banner;
    private final float damagedBelow;
    private final boolean doorOnTop;

    private FortBuildingArt(String roof, String damaged, String wreck, Lights lights, List<Rotor> rotors,
                            Banner banner, float damagedBelow, boolean doorOnTop) {
        this.doorOnTop = doorOnTop;
        this.roof = roof;
        this.damaged = damaged;
        this.wreck = wreck;
        this.lights = lights;
        this.rotors = Collections.unmodifiableList(rotors);
        this.banner = banner;
        this.damagedBelow = damagedBelow;
    }

    public static FortBuildingArt parse(String structureId, JSONObject json) throws JSONException {
        String roof = require(structureId, json, "roof");
        String damaged = require(structureId, json, "damaged");
        String wreck = require(structureId, json, "wreck");
        Lights lights = null;
        JSONObject lightsJson = json.optJSONObject("lights");
        if (lightsJson != null) {
            lights = new Lights(require(structureId, lightsJson, "sprite"),
                    finite(structureId, "lights pulseHz", lightsJson.optDouble("pulseHz", 0.5), 0f, 20f),
                    finite(structureId, "lights pulseDepth", lightsJson.optDouble("pulseDepth", 0.3), 0f, 1f));
        }
        List<Rotor> rotors = new ArrayList<>();
        JSONArray array = json.optJSONArray("rotors");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject r = array.getJSONObject(i);
            float cells = finite(structureId, "rotor cells", r.getDouble("cells"), 0f, 64f);
            if (cells <= 0f) throw new JSONException("Structure '" + structureId + "' rotor " + i + " has no size");
            rotors.add(new Rotor(require(structureId, r, "sprite"),
                    finite(structureId, "rotor x", r.getDouble("x"), -64f, 64f),
                    finite(structureId, "rotor y", r.getDouble("y"), -64f, 64f),
                    cells,
                    finite(structureId, "rotor spin", r.optDouble("spinDegPerSec", 0.0), -3600f, 3600f),
                    finite(structureId, "rotor damagedSpin", r.optDouble("damagedSpin", 0.5), 0f, 1f)));
        }
        Banner banner = null;
        JSONObject bannerJson = json.optJSONObject("banner");
        if (bannerJson != null) {
            float cells = finite(structureId, "banner cells", bannerJson.optDouble("cells", 1.4), 0f, 16f);
            banner = new Banner(finite(structureId, "banner x", bannerJson.getDouble("x"), -64f, 64f),
                    finite(structureId, "banner y", bannerJson.getDouble("y"), -64f, 64f), cells);
        }
        float below = finite(structureId, "damagedBelow",
                json.optDouble("damagedBelow", DEFAULT_DAMAGED_BELOW), 0f, 1f);
        String door = json.optString("door", "bottom").trim();
        if (!door.equals("top") && !door.equals("bottom")) {
            throw new JSONException("Structure '" + structureId + "' building art door must be 'top' or 'bottom'");
        }
        return new FortBuildingArt(roof, damaged, wreck, lights, rotors, banner, below, door.equals("top"));
    }

    private static String require(String structureId, JSONObject json, String key) throws JSONException {
        String value = json.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new JSONException("Structure '" + structureId + "' building art is missing '" + key + "'");
        }
        return value;
    }

    private static float finite(String structureId, String what, double value, float min, float max)
            throws JSONException {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new JSONException("Structure '" + structureId + "' building art " + what + " is out of range");
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

    /**
     * How fast the building's machinery runs, 0 to 1. A wrecked building runs nothing, and a building whose power is
     * not its holder's (cut, contested or taken from the plant that feeds it) runs nothing either; a hurt one runs at
     * half speed.
     */
    public float activity(State state, boolean powered) {
        if (state == State.WRECKED || !powered) return 0f;
        return state == State.DAMAGED ? 0.5f : 1f;
    }

    /** The roof sprite to draw for a standing building, or null when the building is wrecked. */
    public String roofFor(State state) {
        return switch (state) {
            case INTACT -> roof;
            case DAMAGED -> damaged;
            case WRECKED -> null;
        };
    }

    /** The wreck sprite, drawn on the floor once the roof is gone. */
    public String wreckPath() {
        return wreck;
    }

    /** The lights overlay, or null when the building has none. */
    public Lights lights() {
        return lights;
    }

    public List<Rotor> rotors() {
        return rotors;
    }

    /** Where the holder's banner stands, or null for a building that flies none. */
    public Banner banner() {
        return banner;
    }

    /** Whether the art is authored with the building's door on its top edge rather than its bottom edge. */
    public boolean doorOnTop() {
        return doorOnTop;
    }

    /**
     * Whether the art must be turned half a revolution to put its door where a plan's building has it, given whether
     * that building's door is on the top edge of its floor. Registration is kept by turning the rotors and banner too.
     */
    public boolean needsHalfTurn(boolean planDoorOnTop) {
        return doorOnTop != planDoorOnTop;
    }

    public float damagedBelow() {
        return damagedBelow;
    }

    /** Every sprite path this art names, so a catalog check can prove each one exists. */
    public List<String> spritePaths() {
        List<String> paths = new ArrayList<>(List.of(roof, damaged, wreck));
        if (lights != null) paths.add(lights.spritePath());
        for (Rotor rotor : rotors) paths.add(rotor.spritePath());
        return paths;
    }
}
