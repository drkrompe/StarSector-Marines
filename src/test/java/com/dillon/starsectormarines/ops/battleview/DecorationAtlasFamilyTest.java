package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which catalog props a {@link DecorationAtlas} plans for besides the placed
 * ones: the states a battle can swap a placement to. A state it misses still
 * draws, from its own sheet, so the cost of getting this wrong is a bind per
 * damaged prop rather than a missing picture.
 */
class DecorationAtlasFamilyTest {

    @Test
    void aPlacedPropBringsItsStates() {
        Set<String> placed = Set.of("forest.oak", "fort.crate");
        assertTrue(DecorationAtlas.inPlacedFamily("forest.oak-blight-2", placed));
        assertTrue(DecorationAtlas.inPlacedFamily("fort.crate-destroyed", placed));
    }

    @Test
    void aShapedPropBringsItsSiblingsStates() {
        // Cover props name the state between the family and the shape.
        assertTrue(DecorationAtlas.inPlacedFamily("fort.sandbag-damaged-straight",
                Set.of("fort.sandbag-straight")));
    }

    @Test
    void anUnrelatedPropStaysOut() {
        Set<String> placed = Set.of("forest.oak", "fort.sandbag-straight");
        assertFalse(DecorationAtlas.inPlacedFamily("forest.pine-blight-1", placed));
        assertFalse(DecorationAtlas.inPlacedFamily("colony.crate", placed));
    }
}
