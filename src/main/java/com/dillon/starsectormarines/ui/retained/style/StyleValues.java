package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.UiLayout;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The value grammar for the retained CSS property subset. */
final class StyleValues {

    private StyleValues() {
    }

    static StyleEdges parsePadding(String value) {
        return parseInsets(value);
    }

    static Length parseGap(String value) {
        return parseLength(value, false);
    }

    static Object parse(StyleProperty property, String text) {
        String value = text.trim();
        if (value.isEmpty()) throw new UiStyleException(property.cssName() + " needs a value.");
        return switch (property) {
            case FLEX_DIRECTION -> parseDirection(value);
            case WIDTH, HEIGHT -> parseLength(value, true);
            case FLEX_GROW -> nonNegativeNumber(property, value);
            case ROW_GAP, COLUMN_GAP,
                 PADDING_TOP, PADDING_RIGHT, PADDING_BOTTOM, PADDING_LEFT -> parseLength(value, false);
            case BORDER_WIDTH -> parseBorderWidth(value);
            case GAP, PADDING -> throw new IllegalArgumentException("Shorthands expand in StyleDeclaration");
            case OVERFLOW -> parseOverflow(value);
            case BORDER_COLOR, BACKGROUND_COLOR, COLOR -> parseColor(value);
            case FONT_FAMILY -> unquote(value);
            case OPACITY -> opacity(value);
            case TRANSITION -> parseTransitions(value);
        };
    }

    private static UiLayout parseDirection(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "row" -> UiLayout.ROW;
            case "column" -> UiLayout.COLUMN;
            default -> throw new UiStyleException("flex-direction supports row or column, not \""
                    + value + "\".");
        };
    }

    private static Overflow parseOverflow(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "visible" -> Overflow.VISIBLE;
            case "hidden" -> Overflow.HIDDEN;
            case "scroll" -> Overflow.SCROLL;
            default -> throw new UiStyleException("Unsupported overflow value \"" + value + "\".");
        };
    }

    private static float opacity(String value) {
        float result = parseNumber("opacity", value);
        if (result < 0f || result > 1f) {
            throw new UiStyleException("opacity must be between 0 and 1: " + value);
        }
        return result;
    }

    private static float nonNegativeNumber(StyleProperty property, String value) {
        float result = parseNumber(property.cssName(), value);
        if (result < 0f) {
            throw new UiStyleException(property.cssName() + " cannot be negative: " + value);
        }
        return result;
    }

    private static Length parseLength(String value, boolean autoAllowed) {
        Length result = Length.parse(value, autoAllowed);
        if (!result.isNonNegative()) throw new UiStyleException("This length cannot be negative: " + value);
        return result;
    }

    private static Length parseBorderWidth(String value) {
        Length result = parseLength(value, false);
        if (result.containsPercent()) {
            throw new UiStyleException("border-width does not accept percentages: " + value);
        }
        return result;
    }

    private static StyleEdges parseInsets(String value) {
        List<String> parts = splitTopLevelWhitespace(value);
        if (parts.isEmpty() || parts.size() > 4) {
            throw new UiStyleException("padding expects one to four pixel lengths: " + value);
        }
        Length[] parsed = new Length[parts.size()];
        for (int index = 0; index < parts.size(); index++) {
            parsed[index] = parseLength(parts.get(index), false);
        }
        return switch (parsed.length) {
            case 1 -> new StyleEdges(parsed[0], parsed[0], parsed[0], parsed[0]);
            case 2 -> new StyleEdges(parsed[0], parsed[1], parsed[0], parsed[1]);
            case 3 -> new StyleEdges(parsed[0], parsed[1], parsed[2], parsed[1]);
            case 4 -> new StyleEdges(parsed[0], parsed[1], parsed[2], parsed[3]);
            default -> throw new IllegalStateException("unreachable");
        };
    }

    private static List<String> splitTopLevelWhitespace(String value) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') depth--;
            if (Character.isWhitespace(character) && depth == 0) {
                if (start >= 0) {
                    result.add(value.substring(start, index));
                    start = -1;
                }
            } else if (start < 0) {
                start = index;
            }
        }
        if (start >= 0) result.add(value.substring(start));
        return result;
    }

    private static Color parseColor(String value) {
        if (value.equalsIgnoreCase("transparent")) return new Color(0, 0, 0, 0);
        if (!value.startsWith("#")) {
            throw new UiStyleException("Colors use CSS hexadecimal notation or transparent: " + value);
        }
        String hex = value.substring(1);
        try {
            return switch (hex.length()) {
                case 3 -> new Color(expand(hex.charAt(0)), expand(hex.charAt(1)),
                        expand(hex.charAt(2)));
                case 4 -> new Color(expand(hex.charAt(0)), expand(hex.charAt(1)),
                        expand(hex.charAt(2)), expand(hex.charAt(3)));
                case 6 -> new Color(Integer.parseInt(hex, 16));
                case 8 -> new Color(Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16),
                        Integer.parseInt(hex.substring(6, 8), 16));
                default -> throw new UiStyleException("Color must use #rgb, #rgba, #rrggbb, or #rrggbbaa: "
                        + value);
            };
        } catch (NumberFormatException exception) {
            throw new UiStyleException("Malformed hexadecimal color: " + value);
        }
    }

    private static int expand(char digit) {
        int value = Character.digit(digit, 16);
        if (value < 0) throw new NumberFormatException();
        return value * 17;
    }

    private static List<TransitionSpec> parseTransitions(String value) {
        if (value.equalsIgnoreCase("none")) return List.of();
        List<TransitionSpec> result = new ArrayList<>();
        for (String entry : value.split(",")) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length < 2 || parts.length > 4) {
                throw new UiStyleException("transition expects property plus a duration, timing, and optional delay: "
                        + entry.trim());
            }
            StyleProperty property = StyleProperty.parse(parts[0]);
            List<StyleProperty> targets = transitionTargets(property);
            if (targets.isEmpty()) {
                throw new UiStyleException("transition cannot animate " + property.cssName() + ".");
            }
            Float duration = null;
            Easing easing = Easing.EASE;
            boolean easingSeen = false;
            Float delay = null;
            for (int index = 1; index < parts.length; index++) {
                if (isTime(parts[index])) {
                    if (duration == null) duration = parseTime(parts[index]);
                    else if (delay == null) delay = parseTime(parts[index]);
                    else {
                        throw new UiStyleException("transition has more than two times: " + entry.trim());
                    }
                } else {
                    if (easingSeen) throw new UiStyleException("transition names two timing functions: "
                            + entry.trim());
                    easing = Easing.parse(parts[index]);
                    easingSeen = true;
                }
            }
            if (duration == null) {
                throw new UiStyleException("transition needs a duration: " + entry.trim());
            }
            for (StyleProperty target : targets) {
                result.add(new TransitionSpec(target, duration, easing, delay == null ? 0f : delay));
            }
        }
        return List.copyOf(result);
    }

    private static List<StyleProperty> transitionTargets(StyleProperty property) {
        if (property == StyleProperty.GAP) {
            return List.of(StyleProperty.ROW_GAP, StyleProperty.COLUMN_GAP);
        }
        if (property == StyleProperty.PADDING) {
            return List.of(StyleProperty.PADDING_TOP, StyleProperty.PADDING_RIGHT,
                    StyleProperty.PADDING_BOTTOM, StyleProperty.PADDING_LEFT);
        }
        return property.animatable() ? List.of(property) : List.of();
    }

    private static boolean isTime(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith("ms") || lower.endsWith("s");
    }

    private static float parseTime(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        float multiplier;
        String number;
        if (lower.endsWith("ms")) {
            multiplier = 0.001f;
            number = value.substring(0, value.length() - 2);
        } else if (lower.endsWith("s")) {
            multiplier = 1f;
            number = value.substring(0, value.length() - 1);
        } else {
            throw new UiStyleException("Transition times require ms or s: " + value);
        }
        float seconds = parseNumber("transition time", number) * multiplier;
        if (seconds < 0f) throw new UiStyleException("Transition time cannot be negative: " + value);
        return seconds;
    }

    private static float parseNumber(String label, String value) {
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new UiStyleException("Malformed " + label + ": " + value);
        }
    }

    private static String unquote(String value) {
        if (value.contains(",")) {
            throw new UiStyleException("font-family fallback lists are not supported yet: " + value);
        }
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
