package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression guard for the data-driven doodad system: every prop a pool can
 * scatter carries real cover. A bad doodad id in any pool fails loud at resolve
 * time (and at the test bootstrap, which loads every sheet).
 */
public class DoodadMappingParityTest {

    private static GenMappingRegistry loadMapping() throws Exception {
        GenMappingRegistry reg = new GenMappingRegistry();
        for (String p : GenMappingRegistry.BUILTIN_MAPPINGS) {
            reg.ingest(new JSONObject(Files.readString(Paths.get("mod", p.split("/")))));
        }
        return reg;
    }

    @Test
    void coverGoldenPerBucket() throws Exception {
        // Invariant after the gap fix: every prop a pool can scatter carries real
        // cover. Asked of the pools rather than of the whole registry, because a
        // sheet also carries deck markings — bay paving, laid by id and walked
        // straight over. Those used to be literal (col,row) frames and so could
        // not reach a registry at all; they have ids now because a coordinate
        // into a re-packed atlas is a silent lie, and having ids is not the same
        // as being something to hide behind.
        GenMappingRegistry mapping = loadMapping();
        for (String poolId : mapping.doodadPoolNames()) {
            for (DoodadDef d : mapping.doodadPool(poolId)) {
                assertNotEquals(DoodadCover.NONE, d.cover,
                        "scattered prop must carry cover: " + d.id + " (pool " + poolId + ")");
            }
        }
    }
}
