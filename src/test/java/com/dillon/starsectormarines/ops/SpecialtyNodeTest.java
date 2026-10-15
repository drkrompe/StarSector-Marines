package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static com.dillon.starsectormarines.ops.BarracksCommandModel.SpecialtyNode.AVAILABLE;
import static com.dillon.starsectormarines.ops.BarracksCommandModel.SpecialtyNode.LOCKED;
import static com.dillon.starsectormarines.ops.BarracksCommandModel.SpecialtyNode.TRAINED;
import static com.dillon.starsectormarines.ops.BarracksCommandModel.specialtyNode;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where one rank of a specialty path stands: only the next rank, with a point to spend, is clickable. */
public class SpecialtyNodeTest {

    @Test
    public void onlyTheNextRankWithAPointIsAvailable() {
        assertEquals(AVAILABLE, specialtyNode(0, 1, true));
        assertEquals(LOCKED, specialtyNode(0, 2, true), "rank II waits for rank I");
        assertEquals(LOCKED, specialtyNode(0, 1, false), "no point, nothing to train");
        assertEquals(TRAINED, specialtyNode(1, 1, false));
        assertEquals(AVAILABLE, specialtyNode(1, 2, true));
        assertEquals(TRAINED, specialtyNode(2, 2, true));
    }
}
