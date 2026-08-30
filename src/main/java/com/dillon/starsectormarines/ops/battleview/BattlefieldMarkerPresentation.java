package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.awt.Color;
import java.util.Locale;

/**
 * Backend-neutral presentation rules for battlefield objectives and placement
 * previews. Simulation state remains authoritative; this class only turns that
 * state into the compact labels, tones, emphasis, and geometry shared by the
 * live renderers and headless evidence.
 */
public final class BattlefieldMarkerPresentation {

    public static final Color HOSTILE = new Color(0xE8, 0x5B, 0x55);
    public static final Color CONTESTED = new Color(0xF2, 0xC2, 0x56);
    public static final Color SECURED = new Color(0x58, 0xB7, 0xE8);
    public static final Color SABOTAGE = new Color(0xE9, 0x9A, 0x4A);
    public static final Color COMPLETE = new Color(0x79, 0xA1, 0xA9);
    public static final Color VALID = new Color(0x76, 0xD7, 0x9A);
    public static final Color INVALID = new Color(0xEF, 0x6A, 0x5D);
    public static final Color PROGRESS = new Color(0xFF, 0xE0, 0x86);
    public static final Color LABEL = new Color(0xEE, 0xF4, 0xF5);
    public static final Color PLATE = new Color(0x08, 0x10, 0x16, 0xD8);

    private static final float MIN_OBJECTIVE_RADIUS_PX = 11f;
    private static final float MAX_OBJECTIVE_RADIUS_PX = 20f;
    private static final float MIN_TARGET_RADIUS_PX = 18f;

    private BattlefieldMarkerPresentation() { }

    public record ObjectiveMarker(String code, String status, Color tone,
                                  float opacity, float progress,
                                  boolean emphasized) { }

    public record TargetMarker(String label, String status, Color tone,
                               float radiusCells, boolean valid) { }

    public static ObjectiveMarker capture(TacticalNode.Kind kind, int ordinal,
                                          CompoundService.CompoundState state,
                                          float progress) {
        String code = switch (kind) {
            case COMMAND_POST -> "C";
            case BARRACKS -> "B";
            case ARMORY -> "A";
            default -> "?";
        } + Math.max(1, ordinal);
        float clampedProgress = clamp01(progress);
        return switch (state) {
            case DEFENDER_HELD -> new ObjectiveMarker(
                    code, "HOSTILE", HOSTILE, 0.62f, 0f, false);
            case CONTESTED -> new ObjectiveMarker(
                    code, percent(clampedProgress), CONTESTED, 0.96f,
                    clampedProgress, true);
            case MARINE_HELD -> new ObjectiveMarker(
                    code, "SECURED", SECURED, 0.52f, 0f, false);
        };
    }

    public static ObjectiveMarker sabotage(String siteId, boolean complete,
                                            boolean planting, float progress) {
        float clampedProgress = clamp01(progress);
        if (complete) {
            return new ObjectiveMarker(siteCode(siteId), "ARMED", COMPLETE,
                    0.48f, 1f, false);
        }
        if (planting) {
            return new ObjectiveMarker(siteCode(siteId), percent(clampedProgress),
                    SABOTAGE, 0.98f, clampedProgress, true);
        }
        return new ObjectiveMarker(siteCode(siteId), "SABOTAGE", SABOTAGE,
                0.58f, 0f, false);
    }

    public static TargetMarker target(String displayName, boolean valid,
                                      float radiusCells) {
        String label = displayName == null || displayName.isBlank()
                ? "COMMAND POWER" : displayName.toUpperCase(Locale.ROOT);
        return new TargetMarker(label, valid ? "VALID" : "BLOCKED",
                valid ? VALID : INVALID, Math.max(0f, radiusCells), valid);
    }

    public static float objectiveRadius(float cellPx) {
        return clamp(cellPx * 0.78f,
                MIN_OBJECTIVE_RADIUS_PX, MAX_OBJECTIVE_RADIUS_PX);
    }

    public static float targetRadius(float cellPx, float radiusCells) {
        return Math.max(MIN_TARGET_RADIUS_PX,
                Math.max(0f, radiusCells) * cellPx);
    }

    public static float pulse(float phaseSeconds, boolean emphasized) {
        if (!emphasized) return 1f;
        return 1f + 0.035f * (float) Math.sin(
                phaseSeconds * Math.PI * 2.0 * 1.25);
    }

    private static String siteCode(String siteId) {
        String normalized = siteId == null ? "" : siteId.trim();
        int digitStart = normalized.length();
        while (digitStart > 0 && Character.isDigit(normalized.charAt(digitStart - 1))) {
            digitStart--;
        }
        if (digitStart < normalized.length()) {
            String digits = normalized.substring(digitStart).replaceFirst("^0+(?!$)", "");
            return "S" + digits;
        }
        String compact = normalized.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        return compact.isEmpty() ? "S" : compact.substring(0, Math.min(3, compact.length()));
    }

    private static String percent(float progress) {
        return Math.round(progress * 100f) + "%";
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
