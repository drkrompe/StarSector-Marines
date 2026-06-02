package com.dillon.starsectormarines.render2d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link PolyTess} radial tessellation: quad counts (arc fill fraction),
 * the 12-o'clock clockwise start of the arc, and that every vertex lands inside
 * the requested annulus band. Guards the math ported from the former per-pass
 * {@code drawAnnulus}/{@code drawProgressArc} immediate-mode loops.
 */
public class PolyTessTest {

    private static final float CX = 100f, CY = 200f;
    private static final float INNER = 10f, OUTER = 14f;

    @Test
    public void annulusEmitsOneQuadPerSegment() {
        PolyMesh mesh = new PolyMesh(8);
        PolyTess.appendAnnulus(mesh, CX, CY, INNER, OUTER, 48, 1f, 1f, 1f, 1f);
        assertEquals(48, mesh.quadCount());
    }

    @Test
    public void annulusVerticesLieWithinTheBand() {
        PolyMesh mesh = new PolyMesh(8);
        PolyTess.appendAnnulus(mesh, CX, CY, INNER, OUTER, 32, 1f, 1f, 1f, 1f);
        for (int q = 0; q < mesh.quadCount(); q++) {
            for (int c = 0; c < 4; c++) {
                float dx = mesh.vertX(q, c) - CX;
                float dy = mesh.vertY(q, c) - CY;
                float r = (float) Math.hypot(dx, dy);
                assertTrue(r >= INNER - 1e-3f && r <= OUTER + 1e-3f,
                        "vertex radius " + r + " outside [" + INNER + ", " + OUTER + "]");
            }
        }
    }

    @Test
    public void arcQuadCountIsCeilOfFillFraction() {
        PolyMesh mesh = new PolyMesh(8);

        PolyTess.appendArc(mesh, CX, CY, INNER, OUTER, 1f, 32, 1f, 1f, 1f, 1f);
        assertEquals(32, mesh.quadCount(), "full progress fills every segment");

        mesh.reset();
        PolyTess.appendArc(mesh, CX, CY, INNER, OUTER, 0.5f, 32, 1f, 1f, 1f, 1f);
        assertEquals(16, mesh.quadCount(), "half progress fills half the segments");

        mesh.reset();
        PolyTess.appendArc(mesh, CX, CY, INNER, OUTER, 0.01f, 32, 1f, 1f, 1f, 1f);
        assertEquals(1, mesh.quadCount(), "any positive progress fills at least one segment (ceil)");

        mesh.reset();
        PolyTess.appendArc(mesh, CX, CY, INNER, OUTER, 0f, 32, 1f, 1f, 1f, 1f);
        assertEquals(0, mesh.quadCount(), "zero progress emits nothing");
    }

    @Test
    public void arcStartsAtTwelveOClock() {
        PolyMesh mesh = new PolyMesh(8);
        PolyTess.appendArc(mesh, CX, CY, INNER, OUTER, 1f, 32, 1f, 1f, 1f, 1f);
        // First quad, corner 0 = inner-radius vertex at the start angle (π/2):
        // straight up from center → (CX, CY + INNER).
        assertEquals(CX, mesh.vertX(0, 0), 1e-3f);
        assertEquals(CY + INNER, mesh.vertY(0, 0), 1e-3f);
    }

    @Test
    public void resetClearsAndAppendToTransfersEveryQuad() {
        PolyMesh mesh = new PolyMesh(8);
        PolyTess.appendAnnulus(mesh, CX, CY, INNER, OUTER, 12, 1f, 1f, 1f, 1f);
        mesh.reset();
        assertEquals(0, mesh.quadCount());

        PolyTess.appendArc(mesh, CX, CY, INNER, OUTER, 1f, 20, 1f, 1f, 1f, 1f);
        SolidQuadBatch batch = new SolidQuadBatch(4);
        mesh.appendTo(batch);
        assertEquals(20, batch.quadCount(), "every mesh quad replays into the solid batch");
    }
}
