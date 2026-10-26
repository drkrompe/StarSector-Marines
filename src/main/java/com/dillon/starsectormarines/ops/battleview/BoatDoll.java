package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.WeaponSlot;
import com.dillon.starsectormarines.marine.BoatFittingSlot;

import java.util.List;
import java.util.Locale;

/**
 * One boat as the Boat Deck's doll draws her: the hull she is, and each slot
 * she can be fitted in, with where on the hull it sits.
 *
 * <p>The doll is the Mech Lab's socket schematic over a boat. A fitting slot
 * belongs to the whole airframe and so has no place on it; a hardpoint does,
 * and carries the hull-local position, facing and traverse its weapon slot
 * publishes, so the turret spot drawn on the doll and the one marked on the
 * boat in her berth are the place her gun fires from in battle.
 *
 * @param airframe the pattern she is built on, whose sprite and hull geometry the doll draws
 */
public record BoatDoll(ShuttleType airframe, String name, String pattern, List<Slot> slots) {

    /** Prefix of a hardpoint's {@link Slot#key}; the index follows it. */
    public static final String HARDPOINT_KEY = "hardpoint:";

    public BoatDoll {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** The key a fitting slot's card answers to. */
    public static String slotKey(BoatFittingSlot slot) {
        return slot.name().toLowerCase(Locale.ROOT);
    }

    /** The hardpoint a key names, or -1 for a fitting slot's key. */
    public static int hardpointOf(String key) {
        if (key == null || !key.startsWith(HARDPOINT_KEY)) return -1;
        try {
            return Integer.parseInt(key.substring(HARDPOINT_KEY.length()));
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /** What kind of card a slot is drawn as. */
    public enum Kind { PLATING, DRIVE, LIFT, CABIN, EMITTER, POWER, HARDPOINT }

    /**
     * @param key what selecting the card hands back
     * @param itemId the fitting or door gun installed, by its campaign id
     * @param localX cells starboard of the hull centre, for a hardpoint
     * @param localY cells forward of the hull centre, for a hardpoint
     * @param facingDeg where the mount points, degrees off the nose with port positive
     * @param arcDeg how far it traverses about that facing
     * @param placed whether the hull publishes a weapon slot for this hardpoint at all
     */
    public record Slot(String key, Kind kind, String itemId, String title, String fitted, String tier, boolean selected,
                       float localX, float localY, float facingDeg, float arcDeg, boolean placed) {

        public static Slot fitting(BoatFittingSlot slot, String itemId, String title, String fitted, String tier,
                                   boolean selected) {
            Kind kind = switch (slot) {
                case PLATING -> Kind.PLATING;
                case DRIVE -> Kind.DRIVE;
                case LIFT -> Kind.LIFT;
                case CABIN -> Kind.CABIN;
                case EMITTER -> Kind.EMITTER;
                case POWER -> Kind.POWER;
            };
            return new Slot(slotKey(slot), kind, itemId, title, fitted, tier, selected, 0f, 0f, 0f, 0f, false);
        }

        public static Slot hardpoint(int index, String itemId, String title, String fitted, String tier,
                                     boolean selected, WeaponSlot mount) {
            return mount == null
                    ? new Slot(HARDPOINT_KEY + index, Kind.HARDPOINT, itemId, title, fitted, tier, selected,
                            0f, 0f, 0f, 0f, false)
                    : new Slot(HARDPOINT_KEY + index, Kind.HARDPOINT, itemId, title, fitted, tier, selected,
                            mount.localX(), mount.localY(), mount.angleDeg(), mount.arcDeg(), true);
        }
    }
}
