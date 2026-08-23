package com.dillon.starsectormarines.ui.retained.style;

import java.util.Locale;

/** CSS transition timing keywords supported by the retained UI. */
public enum Easing {
    LINEAR,
    EASE,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    public float apply(float progress) {
        float value = clamp(progress);
        return switch (this) {
            case LINEAR -> value;
            case EASE -> cubicBezier(value, 0.25f, 0.1f, 0.25f, 1f);
            case EASE_IN -> cubicBezier(value, 0.42f, 0f, 1f, 1f);
            case EASE_OUT -> cubicBezier(value, 0f, 0f, 0.58f, 1f);
            case EASE_IN_OUT -> cubicBezier(value, 0.42f, 0f, 0.58f, 1f);
        };
    }

    static Easing parse(String text) {
        try {
            return valueOf(text.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new UiStyleException("Unsupported transition timing function \""
                    + text + "\".");
        }
    }

    private static float cubicBezier(float progress, float x1, float y1,
                                     float x2, float y2) {
        float parameter = progress;
        for (int index = 0; index < 8; index++) {
            float error = bezier(parameter, x1, x2) - progress;
            if (Math.abs(error) < 0.0001f) break;
            float slope = bezierSlope(parameter, x1, x2);
            if (Math.abs(slope) < 0.000001f) break;
            parameter -= error / slope;
        }
        return bezier(clamp(parameter), y1, y2);
    }

    private static float bezier(float value, float first, float second) {
        float inverse = 1f - value;
        return 3f * inverse * inverse * value * first
                + 3f * inverse * value * value * second
                + value * value * value;
    }

    private static float bezierSlope(float value, float first, float second) {
        float inverse = 1f - value;
        return 3f * inverse * inverse * first
                + 6f * inverse * value * (second - first)
                + 3f * value * value * (1f - second);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
