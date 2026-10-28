package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.creep.CreepVegetation;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.forest.Undergrowth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NaturalPropShapeTest {

    @Test
    void aDryTwinIsTheSameShapeUnderItsOwnId() {
        String dry = NaturalPropShape.placedId(TreeCanopy.CEDAR, true);
        assertEquals("forest.cedar-dry", dry);
        assertEquals("forest.cedar", NaturalPropShape.placedId(TreeCanopy.CEDAR, false));
        assertEquals(TreeCanopy.CEDAR, NaturalPropShape.find(dry));
        assertEquals(Undergrowth.THICKET, NaturalPropShape.find(NaturalPropShape.placedId(Undergrowth.THICKET, true)));
        assertNull(NaturalPropShape.find(dry + "-damaged"), "find answers intact placements only");
    }

    @Test
    void creepFindsADryTwinsCanopyInEveryState() {
        for (String state : new String[]{"", "-damaged", "-destroyed"}) {
            assertEquals(TreeCanopy.PINE, CreepVegetation.canopy("forest.pine-dry" + state));
        }
    }
}
