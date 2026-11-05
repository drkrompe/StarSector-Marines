package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasMetrics;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

class VehiclePreviewCanvasTest {

    private static final VehicleType ARMY_TANK = VehicleType.BREAKTHROUGH_TANK;
    private static final VehicleType WHITE_RING_TANK = VehicleType.LASER_TANK;

    @Test
    void aBoughtHullStandsWithItsBareRingAndNoTurret() {
        RecordingCanvas canvas = new RecordingCanvas();

        VehiclePreviewCanvas.asBought(ARMY_TANK).drawAt(canvas, 0f, 0f, 10f, true, 1f);

        assertFalse(canvas.drewHullSheetFrame(ARMY_TANK, ARMY_TANK.turretFrame), "no turret from the hull's sheet");
        assertTrue(canvas.paths.contains("graphics/battle/vehicles/" + ARMY_TANK.mountStyle().ringTexture));
    }

    @Test
    void aWhiteRingHasItsCollarInIt() {
        RecordingCanvas canvas = new RecordingCanvas();

        VehiclePreviewCanvas.asBought(WHITE_RING_TANK).drawAt(canvas, 0f, 0f, 10f, true, 1f);

        assertTrue(canvas.paths.contains("graphics/battle/vehicles/turret-collar.png"));
    }

    @Test
    void aStockHullKeepsItsOwnTurret() {
        RecordingCanvas canvas = new RecordingCanvas();

        new VehiclePreviewCanvas(ARMY_TANK).drawAt(canvas, 0f, 0f, 10f, true, 1f);

        assertTrue(canvas.drewHullSheetFrame(ARMY_TANK, ARMY_TANK.turretFrame));
        assertEquals(List.of(), canvas.paths.stream().filter(path -> path.contains("gun-mount")).toList());
    }

    private static final class RecordingCanvas extends CanvasContext {
        final List<String> paths = new ArrayList<>();
        final List<CanvasSpriteRegion> regions = new ArrayList<>();

        RecordingCanvas() {
            super(new CanvasMetrics(new Rect(0, 0, 80, 30), 80, 30, 1), new Rect(0, 0, 80, 30), 1);
        }

        boolean drewHullSheetFrame(VehicleType type, int frame) {
            CanvasSpriteRegion wanted = CanvasSpriteRegion.frame(type.frameCount, 1, frame);
            for (int i = 0; i < paths.size(); i++) {
                if (paths.get(i).equals(type.spritePath) && regions.get(i).equals(wanted)) return true;
            }
            return false;
        }

        @Override public float[] imageSize(String path) { return null; }
        @Override protected void drawFillQuad(float x0, float y0, float x1, float y1,
                                              float x2, float y2, float x3, float y3, Color color) {}
        @Override protected void drawFillRect(float x, float y, float w, float h, Color color) {}
        @Override protected void drawLine(float x0, float y0, float x1, float y1, Color color, float width) {}
        @Override protected void drawText(BitmapFont font, String value, float x, float y, Color color) {}
        @Override protected void drawSprite(String path, SpriteAPI sprite, float x, float y,
                                            float w, float h, float angle, Color tint,
                                            CanvasSpriteRegion region, CanvasBlend blend) {
            paths.add(path);
            regions.add(region);
        }
    }
}
