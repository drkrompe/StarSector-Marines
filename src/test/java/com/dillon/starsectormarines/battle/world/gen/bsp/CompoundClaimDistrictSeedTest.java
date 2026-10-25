package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A district's seed carries the district's compound, so no compound claim takes it as a member. */
class CompoundClaimDistrictSeedTest {

    @Test
    void aCompoundNeverTakesADistrictsSeed() {
        for (BlockKind district : new BlockKind[]{BlockKind.INDUSTRIAL_ZONE, BlockKind.MAIN_STREET,
                BlockKind.CORDONED_FACILITY}) {
            BlockLeaf row = new BlockLeaf(0, 0, 13, 12, false);
            row.kind = BlockKind.FACTORY_ROW;
            BlockLeaf seed = new BlockLeaf(17, 0, 30, 12, false);
            seed.kind = district;
            Map<BlockLeaf, List<BlockLeaf>> adjacency = new IdentityHashMap<>();
            adjacency.put(row, new ArrayList<>(List.of(seed)));
            adjacency.put(seed, new ArrayList<>(List.of(row)));

            List<Compound> claimed = CompoundClaim.claim(List.of(row, seed), adjacency,
                    CompoundClaim.DEFAULT_SPECS, new Random(1));
            for (Compound compound : claimed) assertFalse(compound.members.contains(seed), district.name());
            assertEquals(district, seed.kind);
        }
    }
}
