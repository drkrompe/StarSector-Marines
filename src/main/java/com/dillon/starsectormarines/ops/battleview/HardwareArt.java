package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * The painted Mech Lab icon for each weapon: a flat pictogram in a dark shade and a light
 * highlight, laid over the hardware's coloured bricks so the hardpoint colour is the icon's
 * own ground (after MechWarrior 4's lab). Each drawing's viewBox matches its footprint, so it
 * fills the piece it sits on. Fragmentation racks are their rack plus a burst mark.
 */
final class HardwareArt {
    private static final String ROOT = "graphics/ui/mech-hardware/";
    private static final Map<MechWeaponComponent, SvgAsset> ART = new EnumMap<>(MechWeaponComponent.class);
    private static SvgAsset fragMark;
    private static boolean loaded;

    private HardwareArt() { }

    /** Parses every drawing once; call while constructing a producer, outside paint. */
    static synchronized void preload() {
        if (loaded) return;
        // Every weapon has a drawing; a missing or malformed one is an authoring error, not a fallback.
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            ART.put(component, SvgAsset.load(ROOT + stem(component) + ".svg"));
        }
        fragMark = SvgAsset.load(ROOT + "frag-mark.svg");
        loaded = true;
    }

    /** Draws the icon inside the box; false only for an empty box. */
    static boolean draw(CanvasContext c, MechWeaponComponent component,
                        float x, float y, float width, float height, int alpha) {
        preload();
        SvgAsset art = ART.get(component);
        if (art == null || width <= 0f || height <= 0f) return false;
        float insetX = width * .06f, insetY = height * .06f;
        Color tint = new Color(255, 255, 255, alpha);
        // Edge to edge: each drawing's 75 x 100 cells are the piece's bricks, so a tag authored
        // in the last cell lands in the last brick. The drawings carry their own margins.
        art.draw(c, x, y, width, height, tint);
        if (fragmentation(component) && fragMark != null) {
            float mark = Math.min(width, height) * .42f;
            // Top corner: the bottom of a rack is its stamped SRM/LRM plate.
            fragMark.draw(c, x + width - mark - insetX * .5f, y + insetY * .5f, mark, mark, tint);
        }
        return true;
    }

    private static boolean fragmentation(MechWeaponComponent component) {
        return switch (component) {
            case SRM_5_FRAG, SRM_15_FRAG, LRM_5_FRAG, LRM_15_FRAG -> true;
            default -> false;
        };
    }

    private static String stem(MechWeaponComponent component) {
        return switch (component) {
            case DUAL_CHAINGUNS -> "chaingun-dual";
            case NOSE_CHAINGUN -> "chaingun-nose";
            case DUAL_LINEAR_CANNONS -> "linear-cannon-dual";
            case SINGLE_HEAVY_CANNON -> "heavy-cannon";
            case DUAL_PULSE_LASERS -> "pulse-laser-dual";
            case DUAL_BASTION_AUTOCANNONS -> "bastion-autocannon-dual";
            case DEMOLITION_CANNON -> "demolition-cannon";
            case DUAL_MUSTER_AUTOGUNS -> "muster-autogun-dual";
            case NOSE_MUSTER_AUTOGUN -> "muster-autogun-nose";
            case QUARRY_BREAKER_CANNON -> "quarry-breaker";
            case SRM_5, SRM_5_FRAG -> "srm-5";
            case SRM_15, SRM_15_FRAG -> "srm-15";
            case LRM_5, LRM_5_FRAG -> "lrm-5";
            case LRM_15, LRM_15_FRAG -> "lrm-15";
            case SHOULDER_LASER_CANNON -> "shoulder-laser";
            case THERMAL_LANCE -> "thermal-lance";
            case PIONEER_ROCKET_CRADLE -> "pioneer-rocket";
        };
    }
}
