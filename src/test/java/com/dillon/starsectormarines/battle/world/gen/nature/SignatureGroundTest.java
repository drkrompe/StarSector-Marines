package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules {@link SignatureGround} owns: what each signature asks of the terrain, and where vents may breathe. */
class SignatureGroundTest {

    /** A crater's rim never closes: the bowl is open by at least a third of the way round, so it seals no island. */
    @Test
    void craterRimsStayOpen() {
        for (long seed = 1; seed <= 20; seed++) {
            for (SignatureGround.Crater crater : SignatureGround.craters(160, 120, seed)) {
                if (!crater.rimmed()) continue;
                TerrainPlan.Stroke rim = SignatureGround.rim(crater, 160, 120, seed);
                assertEquals(TerrainPlan.Mode.RAISE, rim.mode());
                assertTrue(SignatureGround.rimSweep(rim, crater) <= Math.toRadians(240), "rim sweeps " + rim);
            }
        }
    }

    /** Only crater rims touch the terrain plan by default: crack fissures are drawn, never cut. */
    @Test
    void otherSignaturesAddNoTerrain() {
        for (SurfaceSignature s : SurfaceSignature.values()) {
            if (s == SurfaceSignature.CRATER_FIELD) continue;
            assertTrue(SignatureGround.strokes(s, 160, 120, 5L).isEmpty(), s.name());
        }
    }

    /** A crack lies wholly on open ground, closes to nothing at its far end, and is never wider than a split. */
    @Test
    void cracksStayOnOpenGroundAndClose() {
        int w = 120, h = 90;
        IntPredicate open = i -> Math.abs(i % w - 60) > 4;
        List<List<float[]>> cracks = SignatureGround.cracks(w, h, 3L, open);
        assertFalse(cracks.isEmpty());
        for (List<float[]> crack : cracks) {
            for (float[] p : crack) assertTrue(open.test((int) p[1] * w + (int) p[0]), "crack on closed ground");
            assertEquals(0f, crack.get(crack.size() - 1)[2]);
            for (float[] p : crack) assertTrue(p[2] <= SignatureGround.SPLIT_WIDTH, "crack " + p[2] + " cells wide");
        }
    }

    /** Vents stand on open ground with their ring open round them, and never crowd one another. */
    @Test
    void ventsAreSpacedOnOpenGround() {
        int w = 120, h = 90;
        // A paved strip down the middle that no vent may touch.
        IntPredicate open = i -> Math.abs(i % w - 60) > 4;
        float[] vents = SignatureGround.vents(w, h, 9L, open);
        assertTrue(vents.length >= 4);
        for (int i = 0; i < vents.length; i += 2) {
            int x = (int) vents[i], y = (int) vents[i + 1];
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                assertTrue(open.test((y + dy) * w + x + dx), "vent on closed ground at " + x + "," + y);
            for (int j = i + 2; j < vents.length; j += 2)
                assertTrue(Math.hypot(vents[i] - vents[j], vents[i + 1] - vents[j + 1]) >= SignatureGround.VENT_SPACING);
        }
    }
}
