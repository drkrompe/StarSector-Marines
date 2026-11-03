package com.dillon.starsectormarines.ui.retained.style;

/**
 * The CSS {@code corner-cut} value: how far each corner of a box is cut back along both its
 * edges, in document pixels, in {@code top-left top-right bottom-right bottom-left} order. It
 * is the chamfered silhouette of the base game's buttons, which a rectangle cannot draw.
 * Painting only: layout and hit-testing keep the rectangle.
 */
public record CornerCut(float topLeft, float topRight, float bottomRight, float bottomLeft) {

    public static final CornerCut NONE = new CornerCut(0f, 0f, 0f, 0f);

    /** One length for every corner, or exactly four. */
    static CornerCut parse(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 1 && parts.length != 4) {
            throw new UiStyleException("corner-cut takes one length or four: " + value);
        }
        float[] cuts = new float[4];
        for (int i = 0; i < 4; i++) {
            cuts[i] = length(parts[parts.length == 1 ? 0 : i], value);
        }
        return new CornerCut(cuts[0], cuts[1], cuts[2], cuts[3]);
    }

    private static float length(String part, String value) {
        String number = part.endsWith("px") ? part.substring(0, part.length() - 2) : part;
        try {
            float length = Float.parseFloat(number);
            if (length >= 0f && Float.isFinite(length) && (part.endsWith("px") || length == 0f)) return length;
        } catch (NumberFormatException ignored) {
            // Reported below with the whole declaration.
        }
        throw new UiStyleException("corner-cut lengths are non-negative pixels: " + value);
    }

    public boolean none() {
        return topLeft == 0f && topRight == 0f && bottomRight == 0f && bottomLeft == 0f;
    }

    /**
     * The clockwise outline of a {@code width} by {@code height} box from its top-left, as XY
     * pairs. A cut is held to half the shorter side, so a small box keeps a body and two cuts
     * on one edge never cross. An uncut corner gives one point rather than two.
     */
    public float[] outline(float width, float height) {
        float limit = Math.min(width, height) / 2f;
        float tl = Math.min(topLeft, limit);
        float tr = Math.min(topRight, limit);
        float br = Math.min(bottomRight, limit);
        float bl = Math.min(bottomLeft, limit);
        float[] points = new float[16];
        int n = 0;
        n = corner(points, n, 0f, tl, tl, 0f, tl);
        n = corner(points, n, width - tr, 0f, width, tr, tr);
        n = corner(points, n, width, height - br, width - br, height, br);
        n = corner(points, n, bl, height, 0f, height - bl, bl);
        float[] outline = new float[n];
        System.arraycopy(points, 0, outline, 0, n);
        return outline;
    }

    private static int corner(float[] points, int n, float x0, float y0, float x1, float y1, float cut) {
        points[n++] = x0;
        points[n++] = y0;
        if (cut > 0f) {
            points[n++] = x1;
            points[n++] = y1;
        }
        return n;
    }
}
