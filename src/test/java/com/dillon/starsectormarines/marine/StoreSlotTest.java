package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreSlotTest {

    @Test
    void marineKitIsFiledByWhatASquadIsIssued() {
        for (LoadoutCard card : LoadoutCards.all()) {
            StoreSlot slot = StoreSlot.of(EquipmentStore.Kind.LOADOUT, card.id());
            assertEquals(card.kind() == LoadoutCard.Kind.SHEET ? StoreSlot.ARMOR_LOADOUT : StoreSlot.WEAPON_LOADOUT,
                    slot, card.id());
            assertEquals(StoreSlot.Section.MARINE, slot.section());
        }
    }

    @Test
    void everyOfferableMechPatternAndBoatPartHasASlotInItsSection() {
        for (MechPattern pattern : MechPattern.offerable()) {
            StoreSlot slot = StoreSlot.of(EquipmentStore.Kind.MECH_PATTERN, pattern.id());
            assertNotNull(slot, pattern.id());
            assertEquals(StoreSlot.Section.MECHS, slot.section(), pattern.id());
        }
        Set<StoreSlot> boatSlots = EnumSet.noneOf(StoreSlot.class);
        for (String id : BoatParts.offerable()) {
            StoreSlot slot = StoreSlot.of(EquipmentStore.Kind.BOAT_PART, id);
            assertNotNull(slot, id);
            assertEquals(StoreSlot.Section.BOATS, slot.section(), id);
            boatSlots.add(slot);
        }
        assertTrue(boatSlots.contains(StoreSlot.BOAT_WEAPON), "door guns are sold");
        assertTrue(boatSlots.contains(StoreSlot.BOAT_ARMOR), "plating is sold");
    }

    @Test
    void boatFittingsFollowTheirOwnSlotAndGunsAreTheirOwn() {
        assertEquals(StoreSlot.BOAT_ENGINE, StoreSlot.of(EquipmentStore.Kind.BOAT_PART, BoatFitting.TUNED_DRIVE.id()));
        assertEquals(StoreSlot.BOAT_EMITTER, StoreSlot.of(EquipmentStore.Kind.BOAT_PART, BoatFitting.WIDE_EMITTER.id()));
        assertEquals(StoreSlot.BOAT_WEAPON, StoreSlot.of(EquipmentStore.Kind.BOAT_PART, BoatGun.ARBALEST.id()));
        assertNull(StoreSlot.of(EquipmentStore.Kind.BOAT_PART, "not-a-part"));
        assertNull(StoreSlot.of(EquipmentStore.Kind.LOADOUT, "not-a-card"));
    }

    @Test
    void everySlotBelongsToExactlyOneSectionInBrowsingOrder() {
        int total = 0;
        for (StoreSlot.Section section : StoreSlot.Section.values()) {
            for (StoreSlot slot : StoreSlot.of(section)) assertEquals(section, slot.section());
            total += StoreSlot.of(section).size();
        }
        assertEquals(StoreSlot.values().length, total);
    }
}
