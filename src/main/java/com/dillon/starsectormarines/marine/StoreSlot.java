package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a thing for sale belongs in the store: a {@link Section} and, within it, the slot of the
 * company's equipment it fills. The store page browses by these; nothing else reads them.
 *
 * <p>Each section is browsed by what a part does, in one vocabulary: a squad's issue (weapon or armor
 * loadout) for marines, and for mechs and vehicles and for shuttles alike the chassis, engine, weapon,
 * armor and the rest, so the same word names the same kind of part in every section. A mech chassis
 * and a vehicle hull are both a chassis; a vehicle kit is the vehicle's take on the equipment a mech's
 * internals give. A shuttle has no chassis to sell: its yard builds the hull.
 */
public enum StoreSlot {
    WEAPON_LOADOUT(Section.MARINE),
    ARMOR_LOADOUT(Section.MARINE),

    MECH_CHASSIS(Section.MECHS),
    MECH_ENGINE(Section.MECHS),
    MECH_WEAPON(Section.MECHS),
    MECH_ARMOR(Section.MECHS),
    MECH_EQUIPMENT(Section.MECHS),

    BOAT_ENGINE(Section.BOATS),
    BOAT_WEAPON(Section.BOATS),
    BOAT_ARMOR(Section.BOATS),
    BOAT_LIFT(Section.BOATS),
    BOAT_CABIN(Section.BOATS),
    BOAT_EMITTER(Section.BOATS),
    BOAT_POWER(Section.BOATS);

    /** The three kinds of equipment the company buys. */
    public enum Section { MARINE, MECHS, BOATS }

    private final Section section;

    StoreSlot(Section section) {
        this.section = section;
    }

    /**
     * The name of this slot's icon, shared by every slot of the same kind of part: a weapon is one
     * icon whether a marine's, a machine's or a shuttle's.
     */
    public String symbol() {
        return switch (this) {
            case WEAPON_LOADOUT, MECH_WEAPON, BOAT_WEAPON -> "weapons";
            case ARMOR_LOADOUT, MECH_ARMOR, BOAT_ARMOR -> "armor";
            case MECH_ENGINE, BOAT_ENGINE -> "engines";
            case MECH_CHASSIS -> "chassis";
            case MECH_EQUIPMENT -> "equipment";
            case BOAT_LIFT -> "lift";
            case BOAT_CABIN -> "cabin";
            case BOAT_EMITTER -> "emitter";
            case BOAT_POWER -> "power";
        };
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
                    case CHASSIS, VEHICLE -> MECH_CHASSIS;
                    case WEAPON -> MECH_WEAPON;
                    case INTERNAL -> switch (pattern.internal().role) {
                        case ENGINE -> MECH_ENGINE;
                        case PLATING -> MECH_ARMOR;
                        default -> MECH_EQUIPMENT;
                    };
                };
            }
            case BOAT_PART -> {
                if (!BoatParts.exists(id)) return null;
                BoatFittingSlot slot = BoatParts.slot(id);
                if (slot == null) return BOAT_WEAPON;
                return switch (slot) {
                    case PLATING -> BOAT_ARMOR;
                    case DRIVE -> BOAT_ENGINE;
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
