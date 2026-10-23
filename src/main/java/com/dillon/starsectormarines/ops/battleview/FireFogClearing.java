package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;

/**
 * At night a burn is seen across the map whoever is watching it: where fire lights the ground, the
 * fog over it thins, so the glow reads through the dark while what stands in it stays hidden (bodies
 * are drawn by line of sight, not by how dark the ground is). One byte per cell, the share of its fog
 * the cell keeps, gathered from {@link FireLight}'s squares at their unflickered strength and rebuilt
 * only when the fire changes. Both fog paths multiply it in.
 *
 * <p>{@code -Dbattle.render.nightFireThroughFog=false} is the control: fog hides fire light as it
 * hides a lamp's, and the night shade puts out whatever burns under it.
 */
final class FireFogClearing {

    static final String PROPERTY = "battle.render.nightFireThroughFog";
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * The most of its fog a cell in full fire light sheds. Under 0.6 or so the fogged burn still reads
     * a step darker than the burn in sight, which is what keeps the edge of sight legible inside it;
     * {@code -Dbattle.render.nightFireFogClears=<0..1>} tries another.
     */
    static final float CLEARS = Float.parseFloat(System.getProperty("battle.render.nightFireFogClears", "0.4"));

    /**
     * Trial switch, off by default: {@code -Dbattle.render.nightUnseenTint=true} draws the night's fog
     * in a cool blue instead of black, so unseen ground reads as unseen even where fire has thinned it.
     */
    static final boolean UNSEEN_TINT = Boolean.parseBoolean(System.getProperty("battle.render.nightUnseenTint", "false"));
    private static final float[] BLACK = {0f, 0f, 0f}, COOL = {0.10f, 0.16f, 0.30f};
    private boolean night;

    private final FireLight fire = new FireLight();
    private FireField builtFrom;
    private long builtRevision = -1;
    private byte[] keep = new byte[0];
    private int gridW, gridH;
    private boolean active;
    private long version;

    /** Brings the clearing up to {@code field}; nothing clears when there is no night or no fire. */
    void update(FireField field, boolean night, int width, int height) {
        this.night = night;
        if (!ENABLED || !night || field == null || field.burningCount() == 0) {
            if (active) version++;
            active = false;
            builtFrom = null;
            return;
        }
        if (active && field == builtFrom && field.revision() == builtRevision
                && width == gridW && height == gridH) return;
        builtFrom = field;
        builtRevision = field.revision();
        gridW = width;
        gridH = height;
        fire.update(field);
        float[] lit = new float[width * height];
        for (int l = 0; l < fire.count(); l++) {
            float r = fire.radius(l), strength = fire.baseStrength(l);
            int x0 = Math.max(0, (int) (fire.x(l) - r)), x1 = Math.min(width - 1, (int) (fire.x(l) + r));
            int y0 = Math.max(0, (int) (fire.y(l) - r)), y1 = Math.min(height - 1, (int) (fire.y(l) + r));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    lit[y * width + x] += fire.pool(l, x + .5f, y + .5f, 0f, strength);
        }
        if (keep.length != lit.length) keep = new byte[lit.length];
        for (int i = 0; i < lit.length; i++)
            keep[i] = (byte) Math.round(255f * (1f - CLEARS * Math.min(1f, lit[i])));
        active = true;
        version++;
    }

    /** The fog's colour: black, or at night under the tint trial a cool blue. */
    float[] fogColour() { return ENABLED && UNSEEN_TINT && night ? COOL : BLACK; }

    /** Whether any fog thins this frame. */
    boolean active() { return active; }

    /** Bumped whenever {@link #keepBytes} changes, so a texture knows to re-upload. */
    long version() { return version; }

    int width() { return gridW; }
    int height() { return gridH; }

    /** One byte per cell, row-major: 255 keeps all the fog, lower sheds it. */
    byte[] keepBytes() { return keep; }

    /** The share of its fog a cell keeps, 0..1. */
    float keep(int index) { return active ? (keep[index] & 0xFF) / 255f : 1f; }
}
