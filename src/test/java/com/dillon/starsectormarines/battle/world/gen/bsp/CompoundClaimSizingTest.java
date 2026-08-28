package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks the functional-building cost of the two-cell apron into compound claiming. */
class CompoundClaimSizingTest {

    @Test
    void walledCompoundsRejectLotsThatTheApronWouldMakeNonFunctional() {
        assertSpec(CompoundClaim.DEFAULT_SPECS, BlockKind.MILITARY_BASE, 8, 9);
        assertSpec(CompoundClaim.CONQUEST_SPECS, BlockKind.MILITARY_BASE, 8, 9);
        assertSpec(CompoundClaim.DEFAULT_SPECS, BlockKind.GATED_HOUSING, 14, 10);
        assertSpec(CompoundClaim.CONQUEST_SPECS, BlockKind.GATED_HOUSING, 14, 10);
    }

    private static void assertSpec(List<CompoundClaim.ClaimSpec> specs,
                                   BlockKind kind,
                                   int seedMin,
                                   int memberMin) {
        CompoundClaim.ClaimSpec spec = specs.stream()
                .filter(candidate -> candidate.seedKind == kind)
                .findFirst()
                .orElseThrow();
        assertEquals(seedMin, spec.seedMinDim);
        assertEquals(memberMin, spec.memberMinDim);
    }
}
