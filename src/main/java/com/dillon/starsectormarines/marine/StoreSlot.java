package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a thing for sale belongs in the store: a {@link Section} and, within it, the slot of the
 * company's equipment it fills. The store page browses by these; nothing else reads them.
 *
 * <p>Marine kit is browsed by what a squad is issued (a weapon loadout or an armor loadout). The
 * mech bay is two sections: the chassis, a mech frame or a vehicle hull, and the parts mechs and
 * vehicles share, browsed by what the part does (weapon, engine, armor, equipment: a vehicle kit is
 * the vehicle's own take on the same equipment a mech's internals give). Boats are browsed by the
 * slot of the boat a part goes into, plus the door guns.
 */
public enum StoreSlot {
    WEAPON_LOADOUT(Section.MARINE),
    ARMOR_LOADOUT(Section.MARINE),

    MECH_CHASSIS(Section.CHASSIS),
    VEHICLE(Section.CHASSIS),

    PART_WEAPON(Section.PARTS),
    PART_ENGINE(Section.PARTS),
    PART_ARMOR(Section.PARTS),
    PART_EQUIPMENT(Section.PARTS),

    BOAT_PLATING(Section.BOATS),
    BOAT_DRIVE(Section.BOATS),
    BOAT_LIFT(Section.BOATS),
    BOAT_CABIN(Section.BOATS),
    BOAT_EMITTER(Section.BOATS),
    BOAT_POWER(Section.BOATS),
    BOAT_GUN(Section.BOATS);

    /** The four kinds of equipment the company buys. */
    public enum Section { MARINE, CHASSIS, PARTS, BOATS }

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
                    case VEHICLE -> VEHICLE;
                    case WEAPON -> PART_WEAPON;
                    case INTERNAL -> switch (pattern.internal().role) {
                        case ENGINE -> PART_ENGINE;
                        case PLATING -> PART_ARMOR;
                        default -> PART_EQUIPMENT;
                    };
                    case KIT -> PART_EQUIPMENT;
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
