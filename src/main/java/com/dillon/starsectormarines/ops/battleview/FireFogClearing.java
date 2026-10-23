package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;

/**
 * At night a burn is seen across the map whoever is watching it: where fire lights the ground, the
 * fog over it thins, so the glow reads through the dark while what stands in it stays hidden (bodies
 * are drawn by line of sight, not by how dark the ground is). One byte per cell, the share of its fog
 * the cell keeps, gathered from {@link FireLight}'s squares at their unflickered strength and rebuilt
 * only when the fire changes. Both fog paths multiply it in.
 */
final class FireFogClearing {

    /**
     * The most of its fog a cell in full fire light sheds. Under 0.6 or so the fogged burn still reads
     * a step darker than the burn in sight, which keeps the edge of sight legible inside it (owner's
     * pick, 2026-10-23).
     */
    static final float CLEARS = 0.4f;

    private final FireLight fire = new FireLight();
    private FireField builtFrom;
    private long builtRevision = -1;
    private byte[] keep = new byte[0];
    private int gridW, gridH;
    private boolean active;
    private long version;

    /** Brings the clearing up to {@code field}; nothing clears when there is no night or no fire. */
    void update(FireField field, boolean night, int width, int height) {
        if (!night || field == null || field.burningCount() == 0) {
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
