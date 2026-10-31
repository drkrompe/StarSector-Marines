package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
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
 * card ({@link VehicleDoll}) draws the same way.
 */
final class HardwareArt {
    private static final String ROOT = "graphics/ui/mech-hardware/";
    private static final Map<MechWeaponComponent, SvgAsset> ART = new EnumMap<>(MechWeaponComponent.class);
    /** The nose drawing of a weapon that wears one on the centreline mount. */
    private static final Map<MechWeaponComponent, SvgAsset> NOSE_ART = new EnumMap<>(MechWeaponComponent.class);
    private static final Map<MechInternal, SvgAsset> INTERNALS = new EnumMap<>(MechInternal.class);
    private static final Map<String, SvgAsset> VEHICLE = new HashMap<>();
    private static SvgAsset fragMark;
    private static boolean loaded;

    private HardwareArt() { }

    /** Parses every drawing once; call while constructing a producer, outside paint. */
    static synchronized void preload() {
        if (loaded) return;
        // Every weapon has a drawing; a missing or malformed one is an authoring error, not a fallback.
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            ART.put(component, SvgAsset.load(ROOT + stem(component) + ".svg"));
            if (component.noseDrawingOn(MechMountSlot.ARMS)) {
                NOSE_ART.put(component, SvgAsset.load(ROOT + stem(component, MechMountSlot.ARMS) + ".svg"));
            }
        }
        for (MechInternal internal : MechInternal.values()) {
            INTERNALS.put(internal, SvgAsset.load(ROOT + stem(internal) + ".svg"));
        }
        for (String stem : VehicleDoll.ART) VEHICLE.put(stem, SvgAsset.load(ROOT + stem + ".svg"));
        fragMark = SvgAsset.load(ROOT + "frag-mark.svg");
        loaded = true;
    }

    /** A sealed vehicle card's drawing ({@link VehicleDoll#art}) inside the box; false for none. */
    static boolean drawVehicle(CanvasContext c, String stem, float x, float y, float width, float height, int alpha) {
        preload();
        return draw(c, stem != null ? VEHICLE.get(stem) : null, x, y, width, height, alpha);
    }

    /** Draws the icon inside the box as the weapon looks off a mech (stores, a vehicle's card); false only for an empty box. */
    static boolean draw(CanvasContext c, MechWeaponComponent component,
                        float x, float y, float width, float height, int alpha) {
        return draw(c, component, null, x, y, width, height, alpha);
    }

    /**
     * Draws the icon of {@code component} fitted on {@code slot}: a chaingun or AC-6 (QS) on the centreline mount
     * wears its nose drawing ({@link MechWeaponComponent#appearanceSelector(MechMountSlot)}); a null slot is off a mech.
     */
    static boolean draw(CanvasContext c, MechWeaponComponent component, MechMountSlot slot,
                        float x, float y, float width, float height, int alpha) {
        preload();
        SvgAsset art = component != null && component.noseDrawingOn(slot) ? NOSE_ART.get(component) : ART.get(component);
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
            case FACTORY_PLATING -> "plating-factory";
            case COMPOSITE_PLATING -> "plating-composite";
            case HEAVY_PLATING -> "plating-heavy";
            // The retired vehicle kits' drawings, the same part on every machine.
            case SPALL_LINER -> "kit-spall-liner";
            case READY_RACK -> "kit-ready-rack";
            case SENSOR_PAIR -> "kit-sensor-pair";
            case ACTIVE_PROTECTION -> "kit-active-protection";
        };
    }

    private static boolean fragmentation(MechWeaponComponent component) {
        return switch (component) {
            case SRM_5_FRAG, SRM_15_FRAG, LRM_5_FRAG, LRM_15_FRAG -> true;
            default -> false;
        };
    }

    /** The drawing {@code component} wears on {@code slot}: its nose drawing on the centreline mount, if it has one. */
    static String stem(MechWeaponComponent component, MechMountSlot slot) {
        if (!component.noseDrawingOn(slot)) return stem(component);
        return switch (component) {
            case AC_6_QS -> "ac-6-qs-nose";
            case CHAINGUN_HF -> "chaingun-hf-nose";
            default -> "chaingun-nose";
        };
    }

    private static String stem(MechWeaponComponent component) {
        return switch (component) {
            case CHAINGUN -> "chaingun";
            case CHAINGUN_HF -> "chaingun-hf";
            case HC_10 -> "hc-10";
            case HC_20 -> "hc-20";
            case PL_2_AP -> "pl-2-ap";
            case AC_6 -> "ac-6";
            case DC_5 -> "dc-5";
            case AC_6_QS -> "ac-6-qs";
            case DC_4_QS -> "dc-4-qs";
            case SRM_5, SRM_5_FRAG -> "srm-5";
            case SRM_15, SRM_15_FRAG -> "srm-15";
            case LRM_5, LRM_5_FRAG -> "lrm-5";
            case LRM_15, LRM_15_FRAG -> "lrm-15";
            case LL_3 -> "ll-3";
            case THERMAL_LANCE -> "thermal-lance";
            case SRM_3_QS -> "srm-3-qs";
            case LIGHT_MACHINE_GUN -> "light-machine-gun";
            case BL_1 -> "bl-1";
            case PL_3 -> "pl-3";
            case BL_2 -> "bl-2";
            case PL_3_SWEEP -> "pl-3-sweep";
            case PL_2 -> "pl-2";
            case PL_1 -> "pl-1";
        };
    }
}
