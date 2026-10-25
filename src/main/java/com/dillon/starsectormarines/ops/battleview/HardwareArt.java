package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The painted Mech Lab icon for each weapon and internal (loaders among them): a flat pictogram in a dark shade and a light
 * highlight, laid over the hardware's coloured bricks so the hardpoint colour is the icon's
 * own ground (after MechWarrior 4's lab). Each drawing's viewBox matches its footprint, so it
 * fills the piece it sits on. Fragmentation racks are their rack plus a burst mark. A sealed vehicle
 * card ({@link VehicleDoll}) and each rack kit draw the same way.
 */
final class HardwareArt {
    private static final String ROOT = "graphics/ui/mech-hardware/";
    private static final Map<MechWeaponComponent, SvgAsset> ART = new EnumMap<>(MechWeaponComponent.class);
    private static final Map<MechInternal, SvgAsset> INTERNALS = new EnumMap<>(MechInternal.class);
    private static final Map<String, SvgAsset> VEHICLE = new HashMap<>();
    private static final Map<VehicleKit, SvgAsset> KITS = new EnumMap<>(VehicleKit.class);
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
        for (MechInternal internal : MechInternal.values()) {
            if (internal.role != MechInternal.Role.PLATING) {
                INTERNALS.put(internal, SvgAsset.load(ROOT + stem(internal) + ".svg"));
            }
        }
        for (String stem : VehicleDoll.ART) VEHICLE.put(stem, SvgAsset.load(ROOT + stem + ".svg"));
        for (VehicleKit kit : VehicleKit.values()) KITS.put(kit, SvgAsset.load(ROOT + stem(kit) + ".svg"));
        fragMark = SvgAsset.load(ROOT + "frag-mark.svg");
        loaded = true;
    }

    /** A sealed vehicle card's drawing ({@link VehicleDoll#art}) inside the box; false for none. */
    static boolean drawVehicle(CanvasContext c, String stem, float x, float y, float width, float height, int alpha) {
        preload();
        return draw(c, stem != null ? VEHICLE.get(stem) : null, x, y, width, height, alpha);
    }

    /** A rack kit's icon inside the box. */
    static boolean draw(CanvasContext c, VehicleKit kit, float x, float y, float width, float height, int alpha) {
        preload();
        return draw(c, KITS.get(kit), x, y, width, height, alpha);
    }

    /** {@code kit.applique-plate} draws from {@code kit-applique-plate.svg}. */
    private static String stem(VehicleKit kit) {
        return kit.id.replace('.', '-');
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

    /** An internal's icon inside the box; false for plating, which has no piece. */
    static boolean draw(CanvasContext c, MechInternal internal,
                        float x, float y, float width, float height, int alpha) {
        preload();
        return draw(c, INTERNALS.get(internal), x, y, width, height, alpha);
    }

    private static boolean draw(CanvasContext c, SvgAsset art,
                                float x, float y, float width, float height, int alpha) {
        if (art == null || width <= 0f || height <= 0f) return false;
        art.draw(c, x, y, width, height, new Color(255, 255, 255, alpha));
        return true;
    }

    private static String stem(MechInternal internal) {
        return switch (internal) {
            case INDUSTRIAL_ENGINE -> "engine-industrial";
            case UPRATED_ENGINE -> "engine-uprated";
            case MILITARY_ENGINE -> "engine-military";
            case FACTORY_CAPACITOR_BANK -> "capacitor-factory";
            case DOUBLE_CAPACITOR_BANK -> "capacitor-double";
            case MILITARY_CAPACITOR_BANK -> "capacitor-military";
            case AUXILIARY_VENT_ARRAY -> "vent-auxiliary";
            case STANDARD_LOADER -> "loader-standard";
            case ACCELERATED_LOADER -> "loader-accelerated";
            case FACTORY_PLATING, COMPOSITE_PLATING, HEAVY_PLATING -> null;
        };
    }

    private static boolean fragmentation(MechWeaponComponent component) {
        return switch (component) {
            case SRM_5_FRAG, SRM_15_FRAG, LRM_5_FRAG, LRM_15_FRAG -> true;
            default -> false;
        };
    }

    private static String stem(MechWeaponComponent component) {
        return switch (component) {
            case CHAINGUN, CHAINGUN_HF -> "chaingun";
            case NOSE_CHAINGUN, NOSE_CHAINGUN_HF -> "chaingun-nose";
            case LINEAR_CANNON -> "linear-cannon";
            case SINGLE_HEAVY_CANNON -> "heavy-cannon";
            case PULSE_LASER -> "pulse-laser";
            case BASTION_AUTOCANNON -> "bastion-autocannon";
            case DEMOLITION_CANNON -> "demolition-cannon";
            case MUSTER_AUTOGUN -> "muster-autogun";
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
