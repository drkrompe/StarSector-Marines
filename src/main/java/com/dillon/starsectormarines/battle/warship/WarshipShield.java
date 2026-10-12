package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;

/**
 * A warship's shield: the major draw on its flux, and what makes the flux budget
 * a trade between the guns and the hull's protection.
 *
 * <p>Owner direction is half of the game's functionality for a brawler, so this is
 * the part that matters to the budget and nothing finer. The figures are the
 * hull's own: {@code shield type} (FRONT, OMNI or none), {@code shield arc},
 * {@code shield upkeep} and {@code shield efficiency} from the hull table and
 * {@code shieldRadius} and {@code shieldCenter} from the hull spec.
 *
 * <ul>
 *   <li><b>Upkeep.</b> While raised the shield draws {@code upkeep} times the
 *       ship's (scaled) dissipation in flux every second. In the game that is the
 *       Onslaught's 240 a second against 600 dissipated.</li>
 *   <li><b>No venting.</b> While raised the pool does not dissipate at all
 *       ({@link WarshipFlux#holdDissipation}). A ship that holds its shield and
 *       keeps firing can only climb.</li>
 *   <li><b>Absorption.</b> A hostile detonation that reaches the hull from inside
 *       the shield's arc is absorbed: its damage times {@code efficiency} becomes
 *       flux and the hull takes nothing. From outside the arc, or while the shield
 *       is down or still rising, it goes to the armour as it always did. The
 *       direction is the blast's bearing from the shield centre (the blast point
 *       stands for where the round came from); a FRONT shield faces the hull's
 *       heading and an OMNI shield turns to any bearing.</li>
 *   <li><b>Collapse.</b> If the pool reaches capacity the shield drops and the
 *       ship overloads, which is {@link WarshipFlux}'s ordinary overload.</li>
 * </ul>
 *
 * <p>In automatic mode (the default) the shield rises when a hostile round that
 * would hurt the hull is near it (landing within {@link #THREAT_MARGIN_CELLS} of
 * the hull, from inside the arc) and drops {@link #HOLD_SECONDS} after the last
 * such round, or at once when the pool passes {@link #DROP_LEVEL}, staying down
 * until it has drained back to {@link WarshipFlux#RESUME_LEVEL}: the ship takes
 * hull damage rather than overload. A manual override ({@link #raise}) holds the
 * shield as commanded until an overload takes it.
 *
 * <p>It works only on a ship that is on flux; a ship with no pool (a scripted
 * cinematic hull) has no shield, so its armour answers as it always has.
 * Deliberately not modelled: hard flux, damage-type multipliers on efficiency,
 * shield-radius interception of rounds in flight, and a shield that stays
 * down while its arc is being rotated.
 */
final class WarshipShield {

    enum Type { NONE, FRONT, OMNI }

    /** Seconds from commanded up to fully absorbing. */
    static final float RAISE_SECONDS = 0.4f;
    /** Seconds from commanded down to gone. */
    static final float LOWER_SECONDS = 0.25f;
    /** Seconds with no threat before an automatic shield drops. */
    static final float HOLD_SECONDS = 3f;
    /** Share of capacity above which an automatic shield drops and stays down (until the resume level). */
    static final float DROP_LEVEL = 0.9f;
    /** How close, beyond the hull, a hostile round has to land to count as a threat. */
    static final float THREAT_MARGIN_CELLS = 6f;
    /** Seconds a hit's brightness takes to fade, for presentation. */
    static final float FLASH_SECONDS = 0.35f;

    private static final float DEFAULT_UPKEEP = 0.4f;
    private static final float DEFAULT_EFFICIENCY = 1f;

    static final WarshipShield NONE = new WarshipShield(Type.NONE, 0f, 0f, 0f, 0f, 0f, 1f);

    private final Type type;
    private final float arcDegrees;
    private final float radiusCells;
    /** Centre in the hull frame, in cells: {@code localX} across the nose, {@code localY} along it. */
    private final float localX;
    private final float localY;
    private final float upkeep;
    private final float efficiency;

    private boolean auto = true;
    private boolean manualUp;
    private boolean raised;
    private float level;
    private float quiet = HOLD_SECONDS;
    private boolean lockedOut;
    private float hitFlash;
    private float dropFlash;
    private int hits;
    private int collapses;
    private float absorbedDamage;
    private float absorbedFlux;

    WarshipShield(Type type, float arcDegrees, float radiusCells, float localX, float localY,
                  float upkeep, float efficiency) {
        this.type = type;
        this.arcDegrees = arcDegrees;
        this.radiusCells = radiusCells;
        this.localX = localX;
        this.localY = localY;
        this.upkeep = upkeep;
        this.efficiency = efficiency;
    }

    /** Reads the shield a hull carries; a hull with none (or a phase cloak) gets {@link #NONE}'s figures. */
    static WarshipShield read(JSONObject hull, Map<String, String> row) {
        Type type = typeOf(row == null ? null : row.get("shield type"));
        if (type == Type.NONE) return new WarshipShield(Type.NONE, 0f, 0f, 0f, 0f, 0f, 1f);
        float arc = type == Type.OMNI ? Math.min(360f, number(row.get("shield arc"), 360f))
                : Math.min(360f, number(row.get("shield arc"), 180f));
        float radiusPx = (float) hull.optDouble("shieldRadius",
                Math.max(hull.optDouble("width", 100.0), hull.optDouble("height", 100.0)) * 0.5);
        JSONArray c = hull.optJSONArray("shieldCenter");
        float vx = c == null ? 0f : (float) c.optDouble(0, 0.0);
        float vy = c == null ? 0f : (float) c.optDouble(1, 0.0);
        // The same frame every slot is read in: the first figure runs along the nose.
        return new WarshipShield(type, arc, WarshipScale.cells(radiusPx),
                -WarshipScale.cells(vy), WarshipScale.cells(vx),
                Math.max(0f, number(row.get("shield upkeep"), DEFAULT_UPKEEP)),
                Math.max(0.01f, number(row.get("shield efficiency"), DEFAULT_EFFICIENCY)));
    }

    private static Type typeOf(String raw) {
        if (raw == null) return Type.NONE;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "FRONT" -> Type.FRONT;
            case "OMNI" -> Type.OMNI;
            default -> Type.NONE;
        };
    }

    private static float number(String raw, float fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            float v = Float.parseFloat(raw.trim());
            return v >= 0f ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    Type type() {
        return type;
    }

    boolean present() {
        return type != Type.NONE;
    }

    float arcDegrees() {
        return arcDegrees;
    }

    float radiusCells() {
        return radiusCells;
    }

    float localX() {
        return localX;
    }

    float localY() {
        return localY;
    }

    /** Upkeep as a share of the ship's dissipation, per second. */
    float upkeep() {
        return upkeep;
    }

    float efficiency() {
        return efficiency;
    }

    /** Whether the shield is commanded up (it draws upkeep and holds the pool from venting). */
    boolean raised() {
        return raised;
    }

    /** 0 to 1: how far the shield has risen. */
    float level() {
        return level;
    }

    /** Whether a hit would be absorbed now. */
    boolean absorbing() {
        return raised && level >= 1f;
    }

    boolean auto() {
        return auto;
    }

    void auto(boolean on) {
        this.auto = on;
    }

    /** Takes the shield out of automatic mode and holds it as commanded. */
    void raise(boolean up) {
        this.auto = false;
        this.manualUp = up;
    }

    /** Brightness of the last absorbed hit, 1 fading to 0. */
    float hitFlash() {
        return hitFlash;
    }

    /** Brightness of the last collapse, 1 fading to 0. */
    float dropFlash() {
        return dropFlash;
    }

    int hits() {
        return hits;
    }

    int collapses() {
        return collapses;
    }

    float absorbedDamage() {
        return absorbedDamage;
    }

    float absorbedFlux() {
        return absorbedFlux;
    }

    /** Whether a blast at {@code bearingDegrees} from the shield centre falls inside the arc of a hull pointing {@code facingDegrees}. */
    boolean covers(float facingDegrees, float bearingDegrees) {
        return switch (type) {
            case NONE -> false;
            case OMNI -> true;
            case FRONT -> Math.abs(WarshipFacing.wrap(bearingDegrees - facingDegrees)) <= arcDegrees * 0.5f;
        };
    }

    /** A hostile round that would hurt the hull is near it; an automatic shield stays up or rises. */
    void noteThreat() {
        quiet = 0f;
    }

    /**
     * Takes one absorbed hit: its damage times efficiency goes into the pool. If
     * that fills the pool the shield collapses with the ship's overload.
     */
    void absorb(float damage, WarshipFlux flux) {
        float add = damage * efficiency;
        hits++;
        absorbedDamage += damage;
        absorbedFlux += add;
        hitFlash = 1f;
        flux.spend(add);
        if (flux.overloaded()) collapse();
    }

    private void collapse() {
        if (raised || level > 0f) {
            collapses++;
            dropFlash = 1f;
        }
        raised = false;
        level = 0f;
    }

    /** Advances the shield one tick: decides whether it is up, pays its upkeep and holds the pool's venting. */
    void update(float dt, WarshipFlux flux, boolean threat) {
        if (!present()) return;
        hitFlash = Math.max(0f, hitFlash - dt / FLASH_SECONDS);
        dropFlash = Math.max(0f, dropFlash - dt / FLASH_SECONDS);
        if (!flux.enabled()) {
            raised = false;
            level = 0f;
            flux.holdDissipation(false);
            return;
        }
        if (flux.overloaded()) {
            collapse();
        } else if (auto) {
            if (threat) quiet = 0f;
            else quiet += dt;
            float fraction = flux.fraction();
            if (fraction >= DROP_LEVEL) lockedOut = true;
            else if (lockedOut && fraction <= WarshipFlux.RESUME_LEVEL) lockedOut = false;
            raised = !lockedOut && (threat || (raised && quiet < HOLD_SECONDS));
        } else {
            raised = manualUp;
        }
        if (raised) level = Math.min(1f, level + dt / RAISE_SECONDS);
        else level = Math.max(0f, level - dt / LOWER_SECONDS);
        if (raised) {
            flux.spend(upkeep * flux.dissipation() * dt);
            if (flux.overloaded()) collapse();
        }
        flux.holdDissipation(raised);
    }
}
