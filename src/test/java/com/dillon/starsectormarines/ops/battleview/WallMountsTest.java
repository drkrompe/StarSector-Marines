package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallMountsTest {

    private static final float EPS = 1e-4f;

    /**
     * An awning's rail sits on its wall's face at the height it hangs from, exactly where the face is painted, wherever
     * the view's centre is; its valance hangs free at its own lift.
     */
    @Test
    void aMountsRailHangsOnTheFaceAsPaintedForAnyViewCentre() {
        // A wall along y = 20 facing -y onto the street, its eave 0.3 in; an awning on the three cells below it.
        float meters = BuildingFaces.HEIGHTS[2], lift = BuildingFaces.lift(meters);
        float z = WallMounts.AWNING_RAIL_METERS / meters;
        float[] eaves = {10f, 20.3f, 13f, 20.3f};
        FacePainter painter = new FacePainter();
        for (float[] c : new float[][]{{11.5f, 5f}, {-30f, 0f}, {50f, -12f}, {11.5f, 19f}}) {
            float[] mount = {10f, 20f, 13f, 20f, 13f, 19f, 10f, 19f};
            WallMounts.standUp(mount, eaves, c[0], c[1], meters, lift, 1f,
                    WallMounts.AWNING_RAIL_METERS, WallMounts.AWNING_VALANCE_METERS, 1f);
            painter.reset();
            painter.wall(10f, 20.3f, 13f, 20.3f, 10f, 20f, 13f, 20f, c[0], c[1], lift, 0f, -1f, 1f);
            painter.quad(0f, 1f, z, z + .1f, 1f, 1f, 1f);
            assertEquals(painter.faces.vertexX(0, 0), mount[0], EPS);
            assertEquals(painter.faces.vertexY(0, 0), mount[1], EPS);
            assertEquals(painter.faces.vertexX(0, 1), mount[2], EPS);
            assertEquals(painter.faces.vertexY(0, 1), mount[3], EPS);
            float valance = BuildingFaces.lift(WallMounts.AWNING_VALANCE_METERS);
            assertEquals(BuildingFaces.lifted(13f, c[0], valance), mount[4], EPS);
            assertEquals(BuildingFaces.lifted(19f, c[1], valance), mount[5], EPS);
        }
    }

    /** A wall cut down to its stub brings what hangs on it down with it. */
    @Test
    void aMountComesDownWithItsCutAwayWall() {
        float meters = BuildingFaces.HEIGHTS[2], lift = BuildingFaces.lift(meters);
        float upright = BuildingFaces.standing(meters, 1f);
        float[] mount = {10f, 20f, 13f, 20f, 13f, 19f, 10f, 19f};
        WallMounts.standUp(mount, new float[]{10f, 20.3f, 13f, 20.3f}, 0f, 0f, meters, lift, upright,
                WallMounts.AWNING_RAIL_METERS, WallMounts.AWNING_VALANCE_METERS, 1f);
        assertEquals(BuildingFaces.onFace(20f, 20.3f, 0f, lift, upright), mount[1], EPS);
        // Its valance hangs as far below the rail as ever.
        float valance = upright * meters - (WallMounts.AWNING_RAIL_METERS - WallMounts.AWNING_VALANCE_METERS);
        assertEquals(BuildingFaces.lifted(19f, 0f, BuildingFaces.lift(valance)), mount[5], EPS);
    }

    /** However it is turned, a mount's frame puts its rail along the wall its north points to. */
    @Test
    void aMountsRailLiesAlongItsWall() {
        DoodadDef awning = new DoodadDef("doodad.street-awning-canvas", "sheet", 0, 0, DoodadCover.NONE, 0f, 3, 1);
        float[] corners = new float[8];
        for (int turns = 0; turns < 4; turns++) {
            Doodad d = new Doodad(4, 6, awning, turns);
            WallMounts.frame(d, corners);
            int[] wall = WallMounts.wallStep(turns);
            float far = wall[0] + wall[1] > 0 ? 1f : 0f;
            // The rail's two corners lie on the footprint's side toward the wall, three cells apart.
            float side = wall[0] != 0 ? d.cellX + far * d.footprintCellsX : d.cellY + far * d.footprintCellsY;
            int axis = wall[0] != 0 ? 0 : 1;
            assertEquals(side, corners[axis], EPS, "turns " + turns);
            assertEquals(side, corners[2 + axis], EPS, "turns " + turns);
            assertEquals(3f, Math.abs(corners[2 + (1 - axis)] - corners[1 - axis]), EPS, "turns " + turns);
            // The outer corners a cell out toward the street.
            assertEquals(side - (wall[0] + wall[1]), corners[4 + axis], EPS, "turns " + turns);
        }
    }

    /** A rail corner's eave is read off the face segment over it, as the face is painted, and inset where none is. */
    @Test
    void aRailCornersEaveIsReadOffItsFaceSegment() {
        float[] segs = new float[BuildingFaces.SEG];
        // Eave from (10, 20.4) to (14, 20.4), turned -y, its foot from (10, 20) to (14, 20).
        float[] seg = {10f, 20.4f, 14f, 20.4f, 0f, -1f, 0f, 0f, 10f, 20f, 14f, 20f};
        System.arraycopy(seg, 0, segs, 0, seg.length);
        float[] out = new float[4];
        WallMounts.eave(segs, 0f, -1f, 11f, 20f, 11.05f, 20f, out, 0);
        assertEquals(11f, out[0], EPS);
        assertEquals(20.4f, out[1], EPS);
        WallMounts.eave(segs, 0f, -1f, 20f, 20f, 20.05f, 20f, out, 2);
        assertEquals(20f, out[2], EPS);
        assertEquals(20f + WallMounts.EAVE_INSET, out[3], EPS);
    }
}
