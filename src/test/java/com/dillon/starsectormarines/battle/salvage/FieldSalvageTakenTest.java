package com.dillon.starsectormarines.battle.salvage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldSalvageTakenTest {

    @Test
    void takenFindsAreLeftOutAndTheRestKeepTheirPlaces() {
        SalvageCache first = new SalvageCache("field-cache-0", 10, 12, SalvageResource.METALS, 40);
        SalvageCache second = new SalvageCache("field-cache-1", 30, 8, SalvageResource.MACHINERY, 8);
        SalvageCache store = new SalvageCache("sealed-store-1", 50, 20, SalvageResource.SUPPLIES, 12);

        List<SalvageCache> left = FieldSalvage.leavingOut(List.of(first, second, store), Set.of("field-cache-0",
                "sealed-store-1"));

        assertEquals(List.of(second), left);
        assertEquals(30, left.get(0).cellX);
    }

    @Test
    void aSiteWithNothingTakenKeepsEveryFind() {
        SalvageCache find = new SalvageCache("field-cache-0", 10, 12, SalvageResource.METALS, 40);
        assertEquals(List.of(find), FieldSalvage.leavingOut(List.of(find), Set.of()));
    }
}
