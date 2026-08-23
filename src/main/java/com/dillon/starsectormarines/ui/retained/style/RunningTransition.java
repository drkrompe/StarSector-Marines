package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.Insets;

import java.awt.Color;
import java.util.Objects;

/** One property of one retained element moving between presented values. */
final class RunningTransition {

    private final StyleProperty property;
    private Object from;
    private Object to;
    private Object reversalTarget;
    private Easing easing;
    private float reversingFactor = 1f;
    private float duration;
    private float delay;
    private float elapsed;

    private RunningTransition(StyleProperty property) {
        this.property = property;
    }

    static RunningTransition start(TransitionSpec spec, Object from, Object to) {
        RunningTransition transition = new RunningTransition(spec.property());
        transition.reversalTarget = from;
        transition.restart(spec, from, to, 1f);
        return transition;
    }

    void retarget(TransitionSpec spec, Object current, Object target) {
        if (Objects.equals(target, to)) {
            easing = spec.easing();
            return;
        }
        if (Objects.equals(target, reversalTarget)) {
            float factor = clamp(easing.apply(progress()) * reversingFactor
                    + 1f - reversingFactor);
            reversalTarget = to;
            reversingFactor = factor;
            restart(spec, current, target, factor);
        } else {
            reversalTarget = current;
            reversingFactor = 1f;
            restart(spec, current, target, 1f);
        }
    }

    private void restart(TransitionSpec spec, Object start, Object target, float factor) {
        easing = spec.easing();
        from = start;
        to = target;
        duration = spec.durationSeconds() * factor;
        delay = spec.delaySeconds() * factor;
        elapsed = 0f;
    }

    void advance(float seconds) {
        elapsed += seconds;
    }

    Object currentValue() {
        if (delayed()) return from;
        if (finished()) return to;
        return interpolate(from, to, easing.apply(progress()));
    }

    boolean delayed() {
        return elapsed < delay;
    }

    boolean finished() {
        return elapsed >= delay + duration;
    }

    StyleProperty property() {
        return property;
    }

    Object target() {
        return to;
    }

    private float progress() {
        if (duration <= 0f) return delayed() ? 0f : 1f;
        return clamp((elapsed - delay) / duration);
    }

    static boolean canInterpolate(Object from, Object to) {
        if (from == null || to == null || from.getClass() != to.getClass()) return false;
        if (from instanceof Float start && to instanceof Float end) {
            return Float.isFinite(start) && Float.isFinite(end);
        }
        if (from instanceof Length start && to instanceof Length end) {
            return start.canInterpolate(end);
        }
        return from instanceof Color || from instanceof Insets;
    }

    private static Object interpolate(Object from, Object to, float progress) {
        if (from instanceof Float start && to instanceof Float end) {
            return lerp(start, end, progress);
        }
        if (from instanceof Insets start && to instanceof Insets end) {
            return new Insets(lerp(start.top(), end.top(), progress),
                    lerp(start.right(), end.right(), progress),
                    lerp(start.bottom(), end.bottom(), progress),
                    lerp(start.left(), end.left(), progress));
        }
        if (from instanceof Length start && to instanceof Length end) {
            return start.interpolate(end, progress);
        }
        if (from instanceof Color start && to instanceof Color end) {
            return interpolateColor(start, end, progress);
        }
        return to;
    }

    private static Color interpolateColor(Color start, Color end, float progress) {
        float startAlpha = start.getAlpha() / 255f;
        float endAlpha = end.getAlpha() / 255f;
        float alpha = lerp(startAlpha, endAlpha, progress);
        if (alpha <= 0f) return new Color(0, 0, 0, 0);
        float red = lerp(start.getRed() / 255f * startAlpha,
                end.getRed() / 255f * endAlpha, progress) / alpha;
        float green = lerp(start.getGreen() / 255f * startAlpha,
                end.getGreen() / 255f * endAlpha, progress) / alpha;
        float blue = lerp(start.getBlue() / 255f * startAlpha,
                end.getBlue() / 255f * endAlpha, progress) / alpha;
        return new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha));
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
