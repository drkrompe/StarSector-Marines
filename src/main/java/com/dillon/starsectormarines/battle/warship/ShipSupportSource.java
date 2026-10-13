package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One ship that can be called over a battle: a frozen loadout and a name, and nothing
 * from the campaign. A committed fleet member, a stock hull from the debug picker and an
 * employer's offered hull all become one of these before the battle starts, so the
 * battle fits and flies every one of them the same way ({@code ShipOnStation}).
 *
 * <p>The loadout is the slot-to-weapon map the member was fitted with at launch,
 * including built-ins. A tender carries no guns of its own (its drones are its weapons),
 * so its map is empty.
 *
 * <p>{@code memberId} is the committed fleet member this ship was frozen from, as the plain
 * id string settlement finds it by; null for a debug or employer ship, which is how those
 * write nothing back.
 */
public record ShipSupportSource(String hullId, String shipName, boolean tender,
                                Map<String, String> weapons, String memberId) implements WarshipLoadout {

    /** The hull a tender is fitted from; mirrors {@code Tender.HULL_ID} without depending on it. */
    public static final String TENDER_HULL_ID = "shepherd";

    public ShipSupportSource {
        if (hullId == null || hullId.isBlank()) throw new IllegalArgumentException("a ship source needs a hull");
        if (shipName == null || shipName.isBlank()) shipName = hullId;
        weapons = weapons == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(weapons));
        if (memberId != null && memberId.isBlank()) memberId = null;
    }

    /** A ship with no campaign member behind it: a stock, debug or employer ship. */
    public ShipSupportSource(String hullId, String shipName, boolean tender, Map<String, String> weapons) {
        this(hullId, shipName, tender, weapons, null);
    }

    /** The same ship, frozen from the fleet member with this id. */
    public ShipSupportSource withMemberId(String id) {
        return new ShipSupportSource(hullId, shipName, tender, weapons, id);
    }

    @Override public String weaponInSlot(String slotId) {
        return weapons.get(slotId);
    }

    /** Freezes a fitted variant: every slot it names, as the live {@link VariantApiLoadout} arms it. */
    public static ShipSupportSource ofVariant(ShipVariantAPI variant, String shipName, boolean tender) {
        VariantApiLoadout loadout = new VariantApiLoadout(variant);
        Set<String> slots = new LinkedHashSet<>(variant.getFittedWeaponSlots());
        if (variant.getHullSpec().getBuiltInWeapons() != null) slots.addAll(variant.getHullSpec().getBuiltInWeapons().keySet());
        Map<String, String> weapons = new LinkedHashMap<>();
        if (!tender) {
            for (String slot : slots) {
                String weapon = loadout.weaponInSlot(slot);
                if (weapon != null && !weapon.isEmpty()) weapons.put(slot, weapon);
            }
        }
        return new ShipSupportSource(loadout.hullId(), shipName, tender, weapons);
    }

    /** A stock variant from the game's data; a tender takes no variant and carries no guns. */
    public static ShipSupportSource stock(String hullId, String variantId, String shipName, boolean tender) {
        Map<String, String> weapons = tender || variantId == null
                ? Map.of() : WarshipVariants.weaponsBySlot(hullId, variantId);
        return new ShipSupportSource(hullId, shipName, tender, weapons);
    }

    /** The stock ship an employer offers when it names a hull; null for a hull with no stock fit. */
    public static ShipSupportSource stockFor(String hullId) {
        if (hullId == null) return null;
        return switch (hullId) {
            case "onslaught" -> stock("onslaught", "onslaught_Standard", "Onslaught", false);
            case "paragon" -> stock("paragon", "paragon_Elite", "Paragon", false);
            case TENDER_HULL_ID -> stock(TENDER_HULL_ID, null, "Shepherd", true);
            default -> null;
        };
    }

    /** Cruisers and capitals are bodies worth calling; a smaller hull stays a smaller hull. */
    public static boolean sizeQualifies(HullSize size) {
        return size == HullSize.CRUISER || size == HullSize.CAPITAL_SHIP;
    }

    /** Whether any weapon in the map is one the warship body can fire at the ground. */
    public static boolean hasGroundGun(Map<String, String> weapons) {
        if (weapons == null) return false;
        for (String id : weapons.values()) {
            VanillaWeapon weapon = VanillaWeapons.get(id);
            if (weapon != null && !weapon.pointDefenceOnly()) return true;
        }
        return false;
    }

    /**
     * Which ship, if any, a hull of this base id and size with these weapons becomes: a tender for
     * the Shepherd, a warship for a cruiser or capital with a ground-reaching gun, nothing else.
     */
    public static ShipSupportSource of(String baseHullId, HullSize size, String shipName,
                                       Map<String, String> weapons) {
        if (TENDER_HULL_ID.equals(baseHullId)) {
            return new ShipSupportSource(baseHullId, shipName, true, Map.of());
        }
        if (!sizeQualifies(size) || !hasGroundGun(weapons)) return null;
        return new ShipSupportSource(baseHullId, shipName, false, weapons);
    }
}
