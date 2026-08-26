package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Live and headless composition of one selected squad inside shipboard quarters. */
public final class BarracksCanvas implements CanvasProducer {

    private static final int URBAN_COLUMNS = 10;
    private static final int URBAN_ROWS = 10;
    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color WHITE = Color.WHITE;
    private static final Color TEAM_FILL = new Color(0x48, 0x94, 0xB3, 18);
    private static final Color TEAM_EDGE = new Color(0x76, 0xB9, 0xD4, 105);
    private static final Color SHADOW = new Color(0, 0, 0, 72);

    private final Supplier<List<MarineSoldier>> marines;
    private final ArmoryLoadoutPreviewComposer.Assets assets;
    private final Supplier<SpriteAPI> tileSheet;
    private final BarracksBattleScene battleScene;
    private final DoubleSupplier elapsedSeconds;

    public BarracksCanvas(Supplier<List<MarineSoldier>> marines,
                          ArmoryLoadoutPreviewComposer.Assets assets,
                          Supplier<SpriteAPI> tileSheet,
                          BarracksBattleScene battleScene,
                          DoubleSupplier elapsedSeconds) {
        if (marines == null) throw new IllegalArgumentException("marines are required");
        if (assets == null) throw new IllegalArgumentException("preview assets are required");
        if (elapsedSeconds == null) throw new IllegalArgumentException("elapsed time is required");
        this.marines = marines;
        this.assets = assets;
        this.tileSheet = tileSheet;
        this.battleScene = battleScene;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        CanvasHostViewport[] liveViewport = new CanvasHostViewport[1];
        boolean liveScene = battleScene != null && context.hostPass((viewport, alphaMult) -> {
            liveViewport[0] = viewport;
            battleScene.renderBackdrop(viewport, alphaMult);
        });

        Projection projection;
        if (liveScene) {
            projection = Projection.forLive(liveViewport[0]);
        } else {
            BattleCamera camera = BarracksBattleScene.cameraForSurface(width, height);
            drawQuarters(context, camera, height, tileSheet != null ? tileSheet.get() : null);
            projection = Projection.forCanvas(camera, height);
        }
        drawTeamZones(context, projection);
        drawMarines(context, projection, marines.get(), (float) elapsedSeconds.getAsDouble());
    }

    private static void drawTeamZones(CanvasContext context, Projection projection) {
        int[] centers = {5, 14, 23};
        for (int center : centers) {
            float left = projection.x(center - 3.5f);
            float right = projection.x(center + 3.5f);
            float top = projection.y(9.5f);
            float bottom = projection.y(3.5f);
            context.fillRect(left, top, right - left, bottom - top, TEAM_FILL);
            context.strokeRect(left, top, right - left, bottom - top, TEAM_EDGE, 1f);
        }
    }

    private void drawMarines(CanvasContext context, Projection projection,
                             List<MarineSoldier> visible, float elapsed) {
        if (visible == null || visible.isEmpty()) return;
        int count = Math.min(visible.size(), BarracksSceneLayout.MARINES.size());
        float shoulder = UnitRenderService.layeredInfantryShoulderWidth(
                Math.min(projection.cellWidth(), projection.cellHeight()),
                UnitType.MARINE.renderScale) * 2.1f;
        for (int index = 0; index < count; index++) {
            MarineSoldier soldier = visible.get(index);
            BarracksSceneLayout.MarinePlacement placement =
                    BarracksSceneLayout.MARINES.get(index);
            float x = projection.x(placement.cellX() + 0.5f);
            float y = projection.y(placement.cellY() + 0.5f);
            context.fillRect(x - shoulder * 0.32f, y + shoulder * 0.28f,
                    shoulder * 0.64f, shoulder * 0.16f, SHADOW);
            LayeredUnitAssets layered = assets.layered(soldier.armor());
            if (layered == null) continue;
            LayerPose idlePose = ArmoryLoadoutPreviewComposer.idlePose(
                    assets.unitLayerLayouts(), elapsed + index * 0.17f);
            LayeredUnitComposer.emit(
                    (layer, centerX, centerY, spriteWidth, spriteHeight, angle,
                     red, green, blue, alpha) -> context.sprite(
                            layer.sourcePath, layer.sprite, centerX,
                            projection.surfaceHeight() - centerY,
                            spriteWidth, spriteHeight, angle,
                            new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha))),
                    layered, layered.head, soldier.primary(), true, soldier.secondary(),
                    soldier.primaryGrade(), x, projection.surfaceHeight() - y, shoulder,
                    placement.facingDegrees(), 0f, 0f, 1f,
                    LayeredAppearance.POSE_IDLE, 0, 1f, idlePose);
        }
    }

    private static void drawQuarters(CanvasContext context, BattleCamera camera,
                                     float height, SpriteAPI liveSheet) {
        context.fillRect(0f, 0f, context.metrics().surfaceWidth(), height, BACKGROUND);
        float cell = camera.cellPxSize();
        for (int y = 0; y < BarracksSceneLayout.HEIGHT; y++) {
            for (int x = 0; x < BarracksSceneLayout.WIDTH; x++) {
                float centerX = camera.cellToScreenX(x + 0.5f);
                float centerY = height - camera.cellToScreenY(y + 0.5f);
                if (BarracksSceneLayout.wall(x, y)) {
                    int column = x == 0 ? 3 : x == BarracksSceneLayout.WIDTH - 1 ? 5 : 4;
                    int row = y == BarracksSceneLayout.HEIGHT - 1 ? 0 : y == 0 ? 2 : 1;
                    drawUrbanTile(context, liveSheet, column, row, centerX, centerY, cell);
                } else {
                    boolean north = BarracksSceneLayout.wall(x, y + 1);
                    boolean south = BarracksSceneLayout.wall(x, y - 1);
                    boolean east = BarracksSceneLayout.wall(x + 1, y);
                    boolean west = BarracksSceneLayout.wall(x - 1, y);
                    int column = west ? 0 : east ? 2 : 1;
                    int row = north ? 0 : south ? 2 : 1;
                    drawUrbanTile(context, liveSheet, column, row, centerX, centerY, cell);
                }
            }
        }
        for (BarracksSceneLayout.FloorOverlayPlacement placement
                : BarracksSceneLayout.floorOverlays()) {
            drawUrbanTile(context, liveSheet, placement.tileColumn(), placement.tileRow(),
                    camera.cellToScreenX(placement.cellX() + 0.5f),
                    height - camera.cellToScreenY(placement.cellY() + 0.5f), cell);
        }
        for (BarracksSceneLayout.PropPlacement placement : BarracksSceneLayout.PROPS) {
            drawUrbanTile(context, liveSheet, placement.tileColumn(), placement.tileRow(),
                    camera.cellToScreenX(placement.cellX() + 0.5f),
                    height - camera.cellToScreenY(placement.cellY() + 0.5f), cell);
        }
    }

    private static void drawUrbanTile(CanvasContext context, SpriteAPI liveSheet,
                                      int column, int row, float centerX, float centerY,
                                      float size) {
        context.sprite(TileManifest.SHEET, liveSheet, centerX, centerY, size, size,
                0f, WHITE, CanvasSpriteRegion.frame(URBAN_COLUMNS, URBAN_ROWS,
                        row * URBAN_COLUMNS + column), CanvasBlend.NORMAL);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private record Projection(float surfaceHeight, float cellWidth, float cellHeight,
                              float offsetX, float offsetY, BattleCamera camera) {

        static Projection forCanvas(BattleCamera camera, float height) {
            return new Projection(height, camera.cellPxSize(), camera.cellPxSize(),
                    0f, 0f, camera);
        }

        static Projection forLive(CanvasHostViewport viewport) {
            BattleCamera camera = BarracksBattleScene.cameraForSurface(
                    viewport.width(), viewport.height());
            return new Projection(viewport.height() / viewport.scaleY(),
                    camera.cellPxSize() / viewport.scaleX(),
                    camera.cellPxSize() / viewport.scaleY(),
                    0f, 0f, camera);
        }

        float x(float worldX) {
            return offsetX + camera.cellToScreenX(worldX)
                    * cellWidth / camera.cellPxSize();
        }

        float y(float worldY) {
            return offsetY + surfaceHeight - camera.cellToScreenY(worldY)
                    * cellHeight / camera.cellPxSize();
        }
    }
}
