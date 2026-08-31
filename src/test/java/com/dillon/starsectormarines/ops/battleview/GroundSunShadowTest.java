package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sun-shadow march, asked directly.
 *
 * <p>{@link GroundSunShadowReference} mirrors the occlusion loop in
 * {@link GroundParallaxPipeline}'s composite; these tests run it against
 * hand-built height fields. That keeps the question about the arithmetic — how
 * far a wall of a stated height reaches under a sun at a stated elevation, which
 * way the shadow falls, and whether an occluder off the edge of the view is in
 * the texture at all — rather than about a generated map, a shader compile, or
 * a GL context.
 *
 * <p>The whole feature rests on one substitution: heights are metres and one
 * cell is one metre ({@link AirScale#METERS_PER_CELL}), so a shadow's length in
 * cells <em>is</em> {@code height / tan(elevation)} with nothing in between.
 * These tests are what hold that identity in place.
 */
class GroundSunShadowTest {

    /** Height of the wall every field here stands up, in metres. */
    private static final float WALL_METERS = 3f;


    // ---------------------------------------------------------------- encode --

    @Test
    void metresSurviveTheEightBitChannelToWithinItsQuantization() {
        float quantum = GroundHeightPass.MACRO_METERS_SPAN / 255f;
        for (float meters : new float[]{-0.5f, -0.25f, 0f, 0.3f, 3f, 12f, 27f}) {
            float channel = GroundHeightPass.encodeMacroMeters(meters);
            float quantized = Math.round(channel * 255f) / 255f;
            assertEquals(meters, GroundHeightPass.decodeMacroMeters(quantized), quantum,
                    meters + " m did not survive the round trip");
        }
    }

    @Test
    void theDatumIsGroundAndTheChannelHoldsWhatIsAuthoredEitherSideOfIt() {
        assertEquals(0f, GroundHeightPass.decodeMacroMeters(GroundHeightPass.MACRO_DATUM), 1e-4f,
                "the datum must decode to the ground plane itself");

        GenMappingRegistry mapping = GenMappingRegistry.installed();
        assertTrue(mapping != null, "global test registry bootstrap did not run");

        float wall = GroundHeightPass.encodeMacroMeters(mapping.wallMacroHeightMeters());
        float water = GroundHeightPass.encodeMacroMeters(
                mapping.macroHeightMeters(CellTopology.GroundKind.WATER));
        assertTrue(wall > GroundHeightPass.MACRO_DATUM && wall < 1f,
                "a wall must encode above the datum without clipping the channel");
        assertTrue(water > 0f && water < GroundHeightPass.MACRO_DATUM,
                "water must encode below the datum without clipping the channel");
    }

    // ---------------------------------------------------------------- length --

    /**
     * The whole point of authoring in metres: shadow length is
     * {@code height / tan(elevation)} in cells, and nothing calibrates it.
     */
    @Test
    void shadowReachesTheLengthTheSunElevationImplies() {
        GroundSunShadowReference.HeightField wall = wallAtColumn(10);

        // tan(45) == 1, so a 3 m wall lays down 3 cells of shadow.
        assertTrue(shadowAt(wall, 9.5f, 0f, 0f, 45f) > 0.99f, "1 cell out must be full shadow");
        assertTrue(shadowAt(wall, 8.5f, 0f, 0f, 45f) > 0.99f, "2 cells out must be full shadow");
        assertEquals(0f, shadowAt(wall, 6.5f, 0f, 0f, 45f), 1e-4f, "4 cells out is past the reach");
        assertEquals(0f, shadowAt(wall, 5.5f, 0f, 0f, 45f), 1e-4f, "5 cells out is past the reach");

        // tan(63.43) == 2, so the same wall reaches only 1.5 cells.
        float steep = (float) Math.toDegrees(Math.atan(2.0));
        assertTrue(shadowAt(wall, 9.5f, 0f, 0f, steep) > 0.99f,
                "a steeper sun still shadows the cell against the wall");
        assertEquals(0f, shadowAt(wall, 7.5f, 0f, 0f, steep), 1e-4f,
                "a steeper sun must pull the far end of the shadow in");
    }

    @Test
    void theWallItselfAndTheSunFacingSideStayLit() {
        GroundSunShadowReference.HeightField wall = wallAtColumn(10);
        assertEquals(0f, shadowAt(wall, 10.5f, 0f, 0f, 45f), 1e-4f,
                "a wall cannot shadow itself");
        assertEquals(0f, shadowAt(wall, 11.5f, 0f, 0f, 45f), 1e-4f,
                "the side the sun is on must stay lit");
    }

    @Test
    void theShadowFallsAwayFromWhicheverBearingTheSunIsOn() {
        GroundSunShadowReference.HeightField wall = wallAtColumn(10);
        assertTrue(shadowAt(wall, 9.5f, 0f, 0f, 45f) > 0.99f,
                "a sun toward +X must shadow the cell on its -X side");
        assertEquals(0f, shadowAt(wall, 9.5f, 0f, 180f, 45f), 1e-4f,
                "turning the sun around must un-shadow that same cell");
        assertTrue(shadowAt(wall, 11.5f, 0f, 180f, 45f) > 0.99f,
                "and shadow the cell opposite it instead");
    }

    @Test
    void aTallerOccluderCastsFurther() {
        float elevation = 45f;
        float shortReach = furthestShadowedCell(wallAtColumn(10, 2f), elevation, 2f);
        float tallReach = furthestShadowedCell(wallAtColumn(10, 6f), elevation, 6f);
        assertTrue(tallReach > shortReach + 2f,
                "6 m must reach materially further than 2 m, got " + tallReach + " vs " + shortReach);
    }

    // ---------------------------------------------------------------- margin --

    /**
     * Why the height target is wider than the view it composites into.
     *
     * <p>A wall standing just off the sun-ward edge is not in frame, but its
     * shadow is. Clamping the height field at the view boundary — which is
     * exactly what a viewport-sized target does, since the texture clamps to
     * edge — deletes that occluder and relights the band it was covering, so
     * the shadow would appear out of nothing as the camera pans onto the wall.
     */
    @Test
    void anOccluderOutsideTheViewStillCastsIntoIt() {
        float viewMinX = 12f;
        GroundSunShadowReference.HeightField whole = wallAtColumn(10);
        GroundSunShadowReference.HeightField clampedToView = (x, y) -> whole.metersAt(Math.max(viewMinX, x), y);

        float sampleX = 12.5f;
        assertTrue(shadowAt(whole, sampleX, 0f, 180f, 45f) > 0.99f,
                "the off-view wall must shadow the first column of the view");
        assertEquals(0f, shadowAt(clampedToView, sampleX, 0f, 180f, 45f), 1e-4f,
                "clamping at the view edge must be what loses it — otherwise this test proves nothing");
    }

    @Test
    void theHeightMarginCoversTheMarchTheSunAsksFor() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();

        for (float elevation : new float[]{
                GroundParallaxPipeline.MIN_SUN_ELEVATION_DEGREES,
                GroundParallaxPipeline.DEFAULT_SUN_ELEVATION_DEGREES,
                GroundParallaxPipeline.MAX_SUN_ELEVATION_DEGREES}) {
            pipeline.setSunElevationDegrees(elevation);
            assertTrue(pipeline.heightPadCells() >= Math.ceil(pipeline.shadowRangeCells()),
                    "at " + elevation + " degrees the margin must cover the march");
        }
    }

    @Test
    void turningShadowsOffGivesBackTheOrdinaryHalo() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();
        pipeline.setSunElevationDegrees(GroundParallaxPipeline.MIN_SUN_ELEVATION_DEGREES);
        assertTrue(pipeline.heightPadCells() > VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                "a low sun should have grown the margin in the first place");

        pipeline.setSunShadowStrength(0f);
        assertEquals(0f, pipeline.shadowRangeCells(), 1e-6f);
        assertEquals(VisibleCellRect.GEOMETRY_MARGIN_CELLS, pipeline.heightPadCells(),
                "shadows off must cost neither the margin's fill nor its memory");
    }

    @Test
    void theMarchIsBoundedEvenAtTheLowestSunTheDialAllows() {
        GroundParallaxPipeline pipeline = new GroundParallaxPipeline();
        pipeline.setSunElevationDegrees(0f); // clamps to the floor
        assertEquals(GroundParallaxPipeline.MIN_SUN_ELEVATION_DEGREES,
                pipeline.sunElevationDegrees(), 1e-4f);
        assertTrue(pipeline.shadowRangeCells() <= GroundParallaxPipeline.MAX_SHADOW_RANGE_CELLS,
                "the tangent must not be allowed to run away");
    }

    // ------------------------------------------------------------------ mirror --

    /** The shared CPU mirror, for a field whose tallest surface is {@link #WALL_METERS}. */
    private static float shadowAt(GroundSunShadowReference.HeightField field,
                                  float worldX, float worldY,
                                  float sunAzimuthDegrees, float sunElevationDegrees) {
        return GroundSunShadowReference.shadowAt(field, worldX, worldY,
                sunAzimuthDegrees, sunElevationDegrees, WALL_METERS);
    }

    private static float shadowAt(GroundSunShadowReference.HeightField field,
                                  float worldX, float worldY,
                                  float sunAzimuthDegrees, float sunElevationDegrees,
                                  float tallestMeters) {
        return GroundSunShadowReference.shadowAt(field, worldX, worldY,
                sunAzimuthDegrees, sunElevationDegrees, tallestMeters);
    }

    /** Cells from the wall to the last fully shadowed sample, marching away from a sun toward +X. */
    private static float furthestShadowedCell(GroundSunShadowReference.HeightField field, float elevationDegrees,
                                              float tallestMeters) {
        float furthest = 0f;
        for (float x = 9.5f; x > -12f; x -= 0.25f) {
            if (shadowAt(field, x, 0f, 0f, elevationDegrees, tallestMeters) > 0.99f) {
                furthest = 10.5f - x;
            }
        }
        return furthest;
    }

    private static GroundSunShadowReference.HeightField wallAtColumn(int column) {
        return wallAtColumn(column, WALL_METERS);
    }

    /**
     * A wall one cell wide running the full height of the map, everything else
     * at the datum. Sampled nearest-cell: the real texture is bilinear, but the
     * question here is the march's arithmetic and a filter would only blur the
     * answer.
     */
    private static GroundSunShadowReference.HeightField wallAtColumn(int column, float meters) {
        return (x, y) -> (int) Math.floor(x) == column ? meters : 0f;
    }
}
