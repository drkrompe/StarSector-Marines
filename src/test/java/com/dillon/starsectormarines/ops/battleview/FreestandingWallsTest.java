package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreestandingWallsTest {

    @Test
    void aWallsHeightFollowsItsKind() {
        assertEquals(FreestandingWalls.Kind.SEALED_PERIMETER, FreestandingWalls.kind("colony.ceramic-wall", 0));
        assertEquals(FreestandingWalls.Kind.SEALED_PERIMETER,
                FreestandingWalls.kind("installation.light-concrete-wall@brutalist", 0));
        assertEquals(FreestandingWalls.Kind.INSTALLATION, FreestandingWalls.kind("installation.dark-concrete-wall", 0));
        assertEquals(FreestandingWalls.Kind.BLAST, FreestandingWalls.kind("installation.light-concrete-wall", 0));
        assertEquals(FreestandingWalls.Kind.BLAST, FreestandingWalls.kind("colony.masonry-wall", 3));
        assertEquals(FreestandingWalls.Kind.COMPOUND, FreestandingWalls.kind("colony.masonry-wall", 0));
        assertEquals(FreestandingWalls.Kind.GARDEN, FreestandingWalls.kind("colony.olive-wall", 0));
        assertEquals(FreestandingWalls.Kind.ENVELOPE, FreestandingWalls.kind("habitat.envelope-wall", 0));
        assertEquals(FreestandingWalls.Kind.ENVELOPE, FreestandingWalls.kind("habitat.envelope-wall@buttress", 0));
        assertEquals(FreestandingWalls.Kind.ENVELOPE_PLINTH, FreestandingWalls.kind("habitat.envelope-wall@plinth", 0));
        assertEquals(FreestandingWalls.Kind.UNKNOWN, FreestandingWalls.kind(null, 0));
        assertEquals(FreestandingWalls.Kind.NONE, FreestandingWalls.kind("ship.neutral-bulkhead", 0));
        int standing = FreestandingWalls.PERIMETER_CELLS;
        for (FreestandingWalls.Kind k : FreestandingWalls.Kind.values())
            assertEquals(k.meters, FreestandingWalls.height(k, standing));
    }

    @Test
    void theSealedPerimeterStandsAboveAGardenWallButNotAPodInItsShell() {
        int standing = FreestandingWalls.PERIMETER_CELLS;
        float sealed = FreestandingWalls.height(FreestandingWalls.Kind.SEALED_PERIMETER, standing);
        assertTrue(sealed > FreestandingWalls.height(FreestandingWalls.Kind.INSTALLATION, standing));
        assertTrue(sealed > FreestandingWalls.height(FreestandingWalls.Kind.GARDEN, standing));
        assertTrue(FreestandingWalls.height(FreestandingWalls.Kind.SEALED_PERIMETER, standing - 1)
                <= FreestandingWalls.height(FreestandingWalls.Kind.COMPOUND, standing));
        assertTrue(FreestandingWalls.height(FreestandingWalls.Kind.INSTALLATION, FreestandingWalls.STUB_CELLS - 1)
                <= FreestandingWalls.height(FreestandingWalls.Kind.COMPOUND, standing), "a ruin's stub");
    }

    @Test
    void anEnvelopeStandsAboveEveryOtherWallOverAPlinthBelowIt() {
        int standing = FreestandingWalls.PERIMETER_CELLS;
        float body = FreestandingWalls.height(FreestandingWalls.Kind.ENVELOPE, standing);
        float plinth = FreestandingWalls.height(FreestandingWalls.Kind.ENVELOPE_PLINTH, standing);
        assertTrue(body >= FreestandingWalls.height(FreestandingWalls.Kind.SEALED_PERIMETER, standing));
        assertTrue(plinth < body);
        assertEquals(FreestandingWalls.Part.BODY, FreestandingWalls.part("habitat.envelope-wall"));
        assertEquals(FreestandingWalls.Part.BUTTRESS, FreestandingWalls.part("habitat.envelope-wall@buttress"));
        assertEquals(FreestandingWalls.Part.PLINTH, FreestandingWalls.part("habitat.envelope-wall@plinth"));
        assertEquals(null, FreestandingWalls.part("installation.dark-concrete-wall"));
    }

    @Test
    void aRunsOutlineIsTheUnionOfItsCells() {
        // An L of three masonry cells, a lone olive cell beside it, a building's wall left out.
        //   y=1: M . .
        //   y=0: M M O
        int w = 4, h = 3;
        Set<Integer> masonry = Set.of(0, 1, w);
        List<FreestandingWalls.Run> runs = FreestandingWalls.runs(w, h,
                at -> masonry.contains(at) || at == 2 || at == 3,
                at -> at == 3 ? FreestandingWalls.Kind.NONE
                        : at == 2 ? FreestandingWalls.Kind.GARDEN : FreestandingWalls.Kind.COMPOUND);
        assertEquals(2, runs.size());
        assertEquals(FreestandingWalls.Kind.GARDEN, runs.get(0).kind(), "lowest first");
        FreestandingWalls.Run l = runs.get(1);
        assertEquals(Set.of(0, 1, w), cells(l.cells()));
        // The L's boundary: 8 unit sides, none between two of its own cells.
        Set<String> sides = new HashSet<>();
        float[] s = l.segments();
        for (int e = 0; e < s.length; e += BuildingFaces.SEG) {
            float mx = (s[e] + s[e + 2]) / 2f, my = (s[e + 1] + s[e + 3]) / 2f;
            sides.add(mx + "," + my + "," + s[e + 4] + "," + s[e + 5]);
            int inside = (int) Math.floor(mx - s[e + 4] * .5f) + (int) Math.floor(my - s[e + 5] * .5f) * w;
            int outside = (int) Math.floor(mx + s[e + 4] * .5f) + (int) Math.floor(my + s[e + 5] * .5f) * w;
            assertTrue(masonry.contains(inside), "inside is the run");
            assertTrue(!masonry.contains(outside) || mx + s[e + 4] * .5f < 0 || my + s[e + 5] * .5f < 0,
                    "outside is not");
        }
        assertEquals(8, sides.size());
        assertEquals(8, s.length / BuildingFaces.SEG);
    }

    private static Set<Integer> cells(int[] cells) {
        Set<Integer> out = new HashSet<>();
        for (int c : cells) out.add(c);
        return out;
    }
}
