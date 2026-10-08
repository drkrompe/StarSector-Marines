package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.CanvasMesh;
import com.dillon.starsectormarines.ui.retained.CanvasMeshBatch;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One CSS linear-gradient, compiled to a unit-square mesh outside painting.
 * The supported subset uses to bottom/right and explicit percentage stops.
 * Transparent colors interpolate in premultiplied sRGB, as CSS requires.
 */
public final class LinearGradient {
    private static final int BANDS_PER_INTERVAL = 24;
    private final boolean horizontal;
    private final List<Stop> stops;
    private final CanvasMeshBatch mesh;

    private LinearGradient(boolean horizontal, List<Stop> stops) {
        this.horizontal = horizontal;
        this.stops = List.copyOf(stops);
        this.mesh = compile();
    }

    public static LinearGradient parse(String value) {
        if (value.equalsIgnoreCase("none")) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("linear-gradient(") || !value.endsWith(")")) {
            throw new UiStyleException("background-image supports none or linear-gradient(to bottom/right, color percent, ...): " + value);
        }
        String[] parts = value.substring(16, value.length() - 1).split(",", -1);
        boolean horizontal = parts[0].trim().equalsIgnoreCase("to right");
        if (!horizontal && !parts[0].trim().equalsIgnoreCase("to bottom")) {
            throw new UiStyleException("linear-gradient requires to bottom or to right: " + value);
        }
        if (parts.length < 3) throw new UiStyleException("linear-gradient needs at least two stops.");
        List<Stop> stops = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String[] stop = parts[i].trim().split("\\s+");
            if (stop.length != 2 || !stop[1].endsWith("%")) {
                throw new UiStyleException("Each gradient stop needs a color and percentage: " + parts[i]);
            }
            float position;
            try {
                position = Float.parseFloat(stop[1].substring(0, stop[1].length() - 1)) / 100f;
            } catch (NumberFormatException invalid) {
                throw new UiStyleException("Invalid gradient stop: " + parts[i]);
            }
            if (!Float.isFinite(position) || position < 0f || position > 1f
                    || (!stops.isEmpty() && position < stops.get(stops.size() - 1).position())) {
                throw new UiStyleException("Gradient stops must be ordered within 0% to 100%.");
            }
            Color color = (Color) StyleValues.parse(StyleProperty.COLOR, stop[0]);
            stops.add(new Stop(position, color));
        }
        return new LinearGradient(horizontal, stops);
    }

    public CanvasMeshBatch mesh() {
        return mesh;
    }

    public Color sample(float position) {
        Stop first = stops.get(0);
        if (position < first.position()) return first.color();
        for (int i = 1; i < stops.size(); i++) {
            Stop left = stops.get(i - 1);
            Stop right = stops.get(i);
            if (position < right.position()) {
                float t = (position - left.position()) / (right.position() - left.position());
                float alpha = mix(left.color().getAlpha(), right.color().getAlpha(), t);
                if (alpha == 0f) return new Color(0, 0, 0, 0);
                return new Color(channel(left.color().getRed(), left.color().getAlpha(),
                                right.color().getRed(), right.color().getAlpha(), t, alpha),
                        channel(left.color().getGreen(), left.color().getAlpha(),
                                right.color().getGreen(), right.color().getAlpha(), t, alpha),
                        channel(left.color().getBlue(), left.color().getAlpha(),
                                right.color().getBlue(), right.color().getAlpha(), t, alpha),
                        Math.round(alpha));
            }
        }
        return stops.get(stops.size() - 1).color();
    }

    private CanvasMeshBatch compile() {
        List<CanvasMeshBatch.Layer> layers = new ArrayList<>();
        float start = 0f;
        for (int index = 0; index <= stops.size(); index++) {
            float end = index == stops.size() ? 1f : stops.get(index).position();
            if (end > start) {
                for (int band = 0; band < BANDS_PER_INTERVAL; band++) {
                    float from = start + (end - start) * band / BANDS_PER_INTERVAL;
                    float to = start + (end - start) * (band + 1) / BANDS_PER_INTERVAL;
                    float x0 = horizontal ? from : 0f, x1 = horizontal ? to : 1f;
                    float y0 = horizontal ? 0f : from, y1 = horizontal ? 1f : to;
                    layers.add(new CanvasMeshBatch.Layer(new CanvasMesh(new float[]{
                            x0, y0, x1, y0, x1, y1, x0, y0, x1, y1, x0, y1
                    }), sample((from + to) * 0.5f)));
                }
            }
            start = end;
        }
        return new CanvasMeshBatch(layers);
    }

    private static int channel(int left, int leftAlpha, int right, int rightAlpha,
                               float t, float alpha) {
        return Math.max(0, Math.min(255, Math.round(mix(left * leftAlpha, right * rightAlpha, t) / alpha)));
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private record Stop(float position, Color color) {}
}
