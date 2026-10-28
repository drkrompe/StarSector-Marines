package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.WeaponSlot;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.BoatHardpoint;

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

    /**
     * Prefix of a hardpoint's {@link Slot#key}; the index follows it. A key for one of the
     * hardpoint's two positions ({@link #positionKey}) adds the position after a second colon.
     */
    public static final String HARDPOINT_KEY = "hardpoint:";

    public BoatDoll {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** The key a fitting slot's card answers to. */
    public static String slotKey(BoatFittingSlot slot) {
        return slot.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The key for one position of a hardpoint, {@link BoatHardpoint#TOP} or
     * {@link BoatHardpoint#BOTTOM}: what the board answers for a point over that row of bricks,
     * so a part goes on or comes off by position.
     */
    public static String positionKey(int hardpoint, int position) {
        return HARDPOINT_KEY + hardpoint + ":" + position;
    }

    /** The hardpoint a key names, a position key's too, or -1 for a fitting slot's key. */
    public static int hardpointOf(String key) {
        if (key == null || !key.startsWith(HARDPOINT_KEY)) return -1;
        String rest = key.substring(HARDPOINT_KEY.length());
        int colon = rest.indexOf(':');
        try {
            return Integer.parseInt(colon < 0 ? rest : rest.substring(0, colon));
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /** The position a hardpoint key names: its own for a position key, the top for a whole hardpoint's. */
    public static int positionOf(String key) {
        if (hardpointOf(key) < 0) return BoatHardpoint.TOP;
        String rest = key.substring(HARDPOINT_KEY.length());
        int colon = rest.indexOf(':');
        if (colon < 0) return BoatHardpoint.TOP;
        try {
            int position = Integer.parseInt(rest.substring(colon + 1));
            return BoatHardpoint.valid(position) ? position : BoatHardpoint.TOP;
        } catch (NumberFormatException malformed) {
            return BoatHardpoint.TOP;
        }
    }

    /** Whether two keys name the same socket: the same fitting slot, or positions of one hardpoint. */
    public static boolean sameSocket(String a, String b) {
        if (a == null || b == null) return false;
        int hardpoint = hardpointOf(a);
        return hardpoint >= 0 ? hardpoint == hardpointOf(b) : a.equals(b);
    }

    /** What kind of card a slot is drawn as. */
    public enum Kind { PLATING, DRIVE, LIFT, CABIN, EMITTER, POWER, HARDPOINT }

    /**
     * @param key what selecting the card hands back
     * @param itemId the fitting or door gun installed, by its campaign id: for a hardpoint, the gun
     *     in its top position, which a 2x2 gun fills whole
     * @param podItemId the 2x1 gun in a hardpoint's bottom position, sharing its turret as a pod;
     *     null for none and for any other socket
     * @param localX cells starboard of the hull centre, for a hardpoint
     * @param localY cells forward of the hull centre, for a hardpoint
     * @param facingDeg where the mount points, degrees off the nose with port positive
     * @param arcDeg how far it traverses about that facing
     * @param placed whether the hull publishes a weapon slot for this hardpoint at all
     * @param missing whether this socket is empty and the boat cannot fly without it: it warns
     */
    public record Slot(String key, Kind kind, String itemId, String podItemId, String title, String fitted, String tier,
                       boolean selected, float localX, float localY, float facingDeg, float arcDeg, boolean placed,
                       boolean missing) {

        /** The item filling one position: a hardpoint's top or bottom, or for any other socket its one part. */
        public String itemAt(int position) {
            return position == BoatHardpoint.BOTTOM ? podItemId : itemId;
        }

        public static Slot fitting(BoatFittingSlot slot, String itemId, String title, String fitted, String tier,
                                   boolean selected, boolean missing) {
            Kind kind = switch (slot) {
                case PLATING -> Kind.PLATING;
                case DRIVE -> Kind.DRIVE;
                case LIFT -> Kind.LIFT;
                case CABIN -> Kind.CABIN;
                case EMITTER -> Kind.EMITTER;
                case POWER -> Kind.POWER;
            };
            return new Slot(slotKey(slot), kind, itemId, null, title, fitted, tier, selected, 0f, 0f, 0f, 0f, false,
                    missing);
        }

        public static Slot hardpoint(int index, String itemId, String podItemId, String title, String fitted,
                                     String tier, boolean selected, WeaponSlot mount) {
            return mount == null
                    ? new Slot(HARDPOINT_KEY + index, Kind.HARDPOINT, itemId, podItemId, title, fitted, tier, selected,
                            0f, 0f, 0f, 0f, false, false)
                    : new Slot(HARDPOINT_KEY + index, Kind.HARDPOINT, itemId, podItemId, title, fitted, tier, selected,
                            mount.localX(), mount.localY(), mount.angleDeg(), mount.arcDeg(), true, false);
        }
    }
}
