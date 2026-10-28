package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a thing for sale belongs in the store: a {@link Section} and, within it, the slot of the
 * company's equipment it fills. The store page browses by these; nothing else reads them.
 *
 * <p>Marine kit is browsed by what a squad is issued (a weapon loadout or an armor loadout), the
 * mech bay by the kind of part (chassis, weapon, internal, vehicle, kit), and boats by the slot of
 * the boat a part goes into, plus the door guns.
 */
public enum StoreSlot {
    WEAPON_LOADOUT(Section.MARINE),
    ARMOR_LOADOUT(Section.MARINE),

    MECH_CHASSIS(Section.MECHS),
    MECH_WEAPON(Section.MECHS),
    MECH_INTERNAL(Section.MECHS),
    VEHICLE(Section.MECHS),
    VEHICLE_KIT(Section.MECHS),

    BOAT_PLATING(Section.BOATS),
    BOAT_DRIVE(Section.BOATS),
    BOAT_LIFT(Section.BOATS),
    BOAT_CABIN(Section.BOATS),
    BOAT_EMITTER(Section.BOATS),
    BOAT_POWER(Section.BOATS),
    BOAT_GUN(Section.BOATS);

    /** The three kinds of equipment the company buys. */
    public enum Section { MARINE, MECHS, BOATS }

    private final Section section;

    StoreSlot(Section section) {
        this.section = section;
    }

    public Section section() {
        return section;
    }

    /** Every slot of one section, in browsing order. */
    public static List<StoreSlot> of(Section section) {
        List<StoreSlot> slots = new ArrayList<>();
        for (StoreSlot slot : values()) {
            if (slot.section == section) slots.add(slot);
        }
        return List.copyOf(slots);
    }

    /** The slot an item of {@code kind} named {@code id} fills; null for an unknown item. */
    static StoreSlot of(EquipmentStore.Kind kind, String id) {
        switch (kind) {
            case LOADOUT -> {
                LoadoutCard card = LoadoutCard.parse(id);
                if (card == null) return null;
                return card.kind() == LoadoutCard.Kind.SHEET ? ARMOR_LOADOUT : WEAPON_LOADOUT;
            }
            case MECH_PATTERN -> {
                MechPattern pattern = MechPattern.parse(id);
                if (pattern == null) return null;
                return switch (pattern.kind()) {
                    case CHASSIS -> MECH_CHASSIS;
                    case WEAPON -> MECH_WEAPON;
                    case INTERNAL -> MECH_INTERNAL;
                    case VEHICLE -> VEHICLE;
                    case KIT -> VEHICLE_KIT;
                };
            }
            case BOAT_PART -> {
                if (!BoatParts.exists(id)) return null;
                BoatFittingSlot slot = BoatParts.slot(id);
                if (slot == null) return BOAT_GUN;
                return switch (slot) {
                    case PLATING -> BOAT_PLATING;
                    case DRIVE -> BOAT_DRIVE;
                    case LIFT -> BOAT_LIFT;
                    case CABIN -> BOAT_CABIN;
                    case EMITTER -> BOAT_EMITTER;
                    case POWER -> BOAT_POWER;
                };
            }
        }
        return null;
    }
}
