package com.dillon.starsectormarines.ui.retained.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A CSS length leaf or {@code min/max/clamp} expression. */
public record Length(Unit unit, float value, List<Length> arguments) {

    public static final float ROOT_EM_PIXELS = 16f;
    public static final Length ZERO = px(0f);
    public static final Length AUTO = new Length(Unit.AUTO, 0f, List.of());

    public Length {
        arguments = List.copyOf(arguments);
    }

    public static Length px(float value) {
        return new Length(Unit.PX, value, List.of());
    }

    public float resolve(float percentageBasis) {
        return switch (unit) {
            case PX -> value;
            case REM -> value * ROOT_EM_PIXELS;
            case PERCENT -> percentageBasis * value * 0.01f;
            case AUTO -> Float.NaN;
            case MIN -> resolveMinimum(percentageBasis);
            case MAX -> resolveMaximum(percentageBasis);
            case CLAMP -> Math.max(arguments.get(0).resolve(percentageBasis),
                    Math.min(arguments.get(1).resolve(percentageBasis),
                            arguments.get(2).resolve(percentageBasis)));
        };
    }

    public boolean canInterpolate(Length other) {
        if (unit != other.unit || unit == Unit.AUTO || arguments.size() != other.arguments.size()) {
            return false;
        }
        for (int index = 0; index < arguments.size(); index++) {
            if (!arguments.get(index).canInterpolate(other.arguments.get(index))) return false;
        }
        return true;
    }

    public boolean isNonNegative() {
        if (arguments.isEmpty()) return unit == Unit.AUTO || value >= 0f;
        for (Length argument : arguments) {
            if (!argument.isNonNegative()) return false;
        }
        return true;
    }

    public boolean containsPercent() {
        if (unit == Unit.PERCENT) return true;
        for (Length argument : arguments) {
            if (argument.containsPercent()) return true;
        }
        return false;
    }

    public Length interpolate(Length other, float progress) {
        if (!canInterpolate(other)) throw new IllegalArgumentException("Length shapes do not match");
        if (arguments.isEmpty()) return new Length(unit, lerp(value, other.value, progress), List.of());
        List<Length> interpolated = new ArrayList<>(arguments.size());
        for (int index = 0; index < arguments.size(); index++) {
            interpolated.add(arguments.get(index).interpolate(other.arguments.get(index), progress));
        }
        return new Length(unit, 0f, interpolated);
    }

    public static Length parse(String source, boolean autoAllowed) {
        String value = source.trim().toLowerCase(Locale.ROOT);
        if (autoAllowed && value.equals("auto")) return AUTO;
        if (value.startsWith("clamp(") && value.endsWith(")")) {
            List<Length> arguments = parseArguments(value, "clamp", false);
            if (arguments.size() != 3) throw new UiStyleException("clamp() needs exactly three lengths: " + source);
            return new Length(Unit.CLAMP, 0f, arguments);
        }
        if (value.startsWith("min(") && value.endsWith(")")) {
            return function(Unit.MIN, value, "min", false);
        }
        if (value.startsWith("max(") && value.endsWith(")")) {
            return function(Unit.MAX, value, "max", false);
        }
        if (value.endsWith("px")) return leaf(Unit.PX, value.substring(0, value.length() - 2), source);
        if (value.endsWith("rem")) return leaf(Unit.REM, value.substring(0, value.length() - 3), source);
        if (value.endsWith("%")) return leaf(Unit.PERCENT, value.substring(0, value.length() - 1), source);
        float number = parseNumber(value, source);
        if (number == 0f) return ZERO;
        throw new UiStyleException("A non-zero CSS length needs px, rem, or %: " + source);
    }

    private static Length function(Unit unit, String source, String name, boolean autoAllowed) {
        List<Length> arguments = parseArguments(source, name, autoAllowed);
        if (arguments.isEmpty()) throw new UiStyleException(name + "() needs at least one length: " + source);
        return new Length(unit, 0f, arguments);
    }

    private static List<Length> parseArguments(String source, String name, boolean autoAllowed) {
        String body = source.substring(name.length() + 1, source.length() - 1);
        List<String> parts = splitArguments(body);
        List<Length> result = new ArrayList<>(parts.size());
        for (String part : parts) result.add(parse(part, autoAllowed));
        return result;
    }

    private static List<String> splitArguments(String source) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') depth--;
            else if (character == ',' && depth == 0) {
                result.add(source.substring(start, index).trim());
                start = index + 1;
            }
            if (depth < 0) throw new UiStyleException("Unbalanced length expression: " + source);
        }
        if (depth != 0) throw new UiStyleException("Unbalanced length expression: " + source);
        result.add(source.substring(start).trim());
        if (result.stream().anyMatch(String::isEmpty)) {
            throw new UiStyleException("Empty length argument: " + source);
        }
        return result;
    }

    private static Length leaf(Unit unit, String number, String source) {
        float parsed = parseNumber(number.trim(), source);
        return new Length(unit, parsed, List.of());
    }

    private static float parseNumber(String value, String source) {
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new UiStyleException("Malformed CSS length: " + source);
        }
    }

    private float resolveMinimum(float basis) {
        float result = Float.POSITIVE_INFINITY;
        for (Length argument : arguments) result = Math.min(result, argument.resolve(basis));
        return result;
    }

    private float resolveMaximum(float basis) {
        float result = Float.NEGATIVE_INFINITY;
        for (Length argument : arguments) result = Math.max(result, argument.resolve(basis));
        return result;
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public enum Unit {
        PX,
        REM,
        PERCENT,
        AUTO,
        MIN,
        MAX,
        CLAMP
    }
}
