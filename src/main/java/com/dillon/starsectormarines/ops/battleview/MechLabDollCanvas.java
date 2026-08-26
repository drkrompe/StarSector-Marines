package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Canonically composed lance inside a top-down battle-tileset fabrication garage. */
public final class MechLabDollCanvas implements CanvasProducer {

    private static final String ROOT = "graphics/battle/mech-modular-topdown/";
    private static final int URBAN_COLUMNS = 10;
    private static final int URBAN_ROWS = 10;
    private static final float GANTRY_FACING_DEGREES = 180f;
    private static final float MIN_DROP_TARGET_WIDTH = 64f;
    private static final float MIN_DROP_TARGET_HEIGHT = 38f;
    private static final float CAPACITY_INSET = 4f;
    private static final float CAPACITY_GAP = 2f;
    private static final Color BACKGROUND = new Color(0x06, 0x0A, 0x10);
    private static final Color WELD = new Color(0xA5, 0xE8, 0xFF);
    private static final Color WHITE = Color.WHITE;

    private final Supplier<List<MechVariant>> variants;
    private final IntSupplier selectedGantry;
    private final Supplier<SocketId> selectedSocket;
    private final Supplier<LayeredMechAssets> assets;
    private final Supplier<LayeredUnitAssets> technicianAssets;
    private final Supplier<SpriteAPI> tileSheet;
    private final MechLabBattleScene battleScene;
    private final DoubleSupplier elapsedSeconds;

    public MechLabDollCanvas(Supplier<MechVariant> variant,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet) {
        this(variant, selectedSocket, assets, technicianAssets, tileSheet,
                () -> null, null, () -> 0d);
    }

    public MechLabDollCanvas(Supplier<MechVariant> variant,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet,
                             DoubleSupplier elapsedSeconds) {
        this(variant, selectedSocket, assets, technicianAssets, tileSheet,
                () -> null, null, elapsedSeconds);
    }

    public MechLabDollCanvas(Supplier<MechVariant> variant,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet,
                             Supplier<SpriteAPI> roadSheet,
                             MechLabBattleScene battleScene,
                             DoubleSupplier elapsedSeconds) {
        this(singletonVariants(variant), () -> 0, selectedSocket, assets,
                technicianAssets, tileSheet, roadSheet, battleScene, elapsedSeconds);
    }

    public MechLabDollCanvas(Supplier<List<MechVariant>> variants,
                             IntSupplier selectedGantry,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet,
                             Supplier<SpriteAPI> roadSheet,
                             MechLabBattleScene battleScene,
                             DoubleSupplier elapsedSeconds) {
        if (variants == null || selectedGantry == null || selectedSocket == null
                || assets == null || technicianAssets == null
                || tileSheet == null || roadSheet == null || elapsedSeconds == null) {
            throw new IllegalArgumentException(
                    "variants, gantry, socket, mech/technician/tile assets, and elapsed time are required");
        }
        this.variants = variants;
        this.selectedGantry = selectedGantry;
        this.selectedSocket = selectedSocket;
        this.assets = assets;
        this.technicianAssets = technicianAssets;
        this.tileSheet = tileSheet;
        this.battleScene = battleScene;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float time = (float) elapsedSeconds.getAsDouble();
        List<MechVariant> lance = variants.get();
        int gantryIndex = selectedIndex(lance, selectedGantry.getAsInt());
        MechVariant selected = gantryIndex >= 0 ? lance.get(gantryIndex) : null;
        LayeredMechAssets sprites = assets.get();
        if (selected == null || sprites == null) return;

        CanvasHostViewport[] liveViewport = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = battleScene != null
                ? battleScene.backdropPass(lance, gantryIndex) : null;
        boolean liveScene = backdrop != null && context.hostPass(new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                liveViewport[0] = viewport;
                return backdrop.prepare(viewport, alphaMult);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                liveViewport[0] = viewport;
                backdrop.draw(viewport, alphaMult);
            }
        });
        SceneProjection projection;
        BattleCamera sceneCamera;
        if (liveScene) {
            CanvasHostViewport viewport = liveViewport[0];
            sceneCamera = MechLabBattleScene.cameraForSurface(
                    viewport.width(), viewport.height(), gantryIndex);
            projection = SceneProjection.forLive(sceneCamera, viewport, selected, gantryIndex);
        } else {
            sceneCamera = MechLabBattleScene.cameraForSurface(width, height, gantryIndex);
            projection = SceneProjection.forCanvas(sceneCamera, height, selected, gantryIndex);
            drawGarage(context, sceneCamera, height, tileSheet.get());
        }

        if (liveScene) {
            context.hostPass(battleScene.actorPass(lance, gantryIndex));
        } else {
            drawLance(context, sceneCamera, height, sprites, lance);
            drawTechnicians(context, sceneCamera, height, projection.cellX(),
                    technicianAssets.get());
        }
        drawWeld(context, projection, time);
        drawSocketOverlays(context, MechFittingLayout.forVariant(selected),
                selectedSocket.get(), projection);
    }

    private static void drawGarage(CanvasContext c, BattleCamera camera, float height,
                                   SpriteAPI urbanSheet) {
        c.fillRect(0f, 0f, c.metrics().surfaceWidth(), height, BACKGROUND);
        float cell = camera.cellPxSize();
        for (int y = 0; y < MechLabSceneLayout.HEIGHT; y++) {
            for (int x = 0; x < MechLabSceneLayout.WIDTH; x++) {
                float centerX = camera.cellToScreenX(x + 0.5f);
                float centerY = height - camera.cellToScreenY(y + 0.5f);
                if (MechLabSceneLayout.wall(x, y)) {
                    int column = x == 0 ? 3 : x == MechLabSceneLayout.WIDTH - 1 ? 5 : 4;
                    int row = y == MechLabSceneLayout.HEIGHT - 1 ? 0 : y == 0 ? 2 : 1;
                    drawUrbanTile(c, urbanSheet, column, row, centerX, centerY, cell);
                    continue;
                }
                boolean northWall = MechLabSceneLayout.wall(x, y + 1);
                boolean southWall = MechLabSceneLayout.wall(x, y - 1);
                boolean eastWall = MechLabSceneLayout.wall(x + 1, y);
                boolean westWall = MechLabSceneLayout.wall(x - 1, y);
                int column = westWall ? 0 : eastWall ? 2 : 1;
                int row = northWall ? 0 : southWall ? 2 : 1;
                drawUrbanTile(c, urbanSheet, column, row,
                        centerX, centerY, cell);
            }
        }
        for (MechLabSceneLayout.FloorOverlayPlacement overlay
                : MechLabSceneLayout.floorOverlays()) {
            drawUrbanTile(c, urbanSheet, overlay.tileColumn(), overlay.tileRow(),
                    camera.cellToScreenX(overlay.cellX() + 0.5f),
                    height - camera.cellToScreenY(overlay.cellY() + 0.5f), cell);
        }
        for (MechLabSceneLayout.PropPlacement prop : MechLabSceneLayout.PROPS) {
            drawUrbanTile(c, urbanSheet, prop.tileColumn(), prop.tileRow(),
                    camera.cellToScreenX(prop.cellX() + 0.5f),
                    height - camera.cellToScreenY(prop.cellY() + 0.5f), cell);
        }
    }

    private static void drawUrbanTile(CanvasContext c, SpriteAPI liveSheet,
                                      int column, int row, float centerX, float centerY,
                                      float size) {
        drawTile(c, TileManifest.SHEET, liveSheet, URBAN_COLUMNS, URBAN_ROWS,
                column, row, centerX, centerY, size);
    }

    private static void drawTile(CanvasContext c, String path, SpriteAPI liveSheet,
                                 int columns, int rows, int column, int row,
                                 float centerX, float centerY, float size) {
        c.sprite(path, liveSheet, centerX, centerY, size, size,
                0f, WHITE, CanvasSpriteRegion.frame(columns, rows,
                        row * columns + column), CanvasBlend.NORMAL);
    }

    private static void drawTechnicians(CanvasContext c, BattleCamera camera, float height,
                                        float cell, LayeredUnitAssets crew) {
        if (crew == null) return;
        float shoulder = UnitRenderService.layeredInfantryShoulderWidth(
                cell, UnitType.ENGINEER.renderScale);
        float[] facings = {90f, 250f, 70f, 290f};
        for (int index = 0; index < MechLabSceneLayout.TECHNICIANS.size(); index++) {
            MechLabSceneLayout.TechnicianPlacement technician =
                    MechLabSceneLayout.TECHNICIANS.get(index);
            drawTechnician(c, crew,
                    camera.cellToScreenX(technician.cellX() + 0.5f),
                    height - camera.cellToScreenY(technician.cellY() + 0.5f),
                    shoulder, facings[index], 0f, false);
        }
    }

    private static void drawLance(CanvasContext context, BattleCamera camera, float height,
                                  LayeredMechAssets sprites, List<MechVariant> variants) {
        int count = Math.min(variants.size(), MechLabSceneLayout.GANTRIES.size());
        for (int index = 0; index < count; index++) {
            MechVariant variant = variants.get(index);
            MechLabSceneLayout.Gantry gantry = MechLabSceneLayout.GANTRIES.get(index);
            float hull = UnitRenderService.layeredMechHullWidth(
                    camera.cellPxSize(), variant.renderScale);
            LayeredMechComposer.emit(new CanvasSink(context, height), sprites,
                    camera.cellToScreenX(gantry.cellX() + 0.5f),
                    camera.cellToScreenY(gantry.cellY() + 0.5f), hull,
                    GANTRY_FACING_DEGREES, GANTRY_FACING_DEGREES,
                    0f, 0f, 0f, 0f, 0,
                    variant.chassisAppearance, variant.arms.appearanceSelector,
                    appearance(variant.leftShoulder), appearance(variant.rightShoulder), 1f);
        }
    }

    private static Supplier<List<MechVariant>> singletonVariants(
            Supplier<MechVariant> variant) {
        if (variant == null) throw new IllegalArgumentException("variant is required");
        return () -> {
            MechVariant selected = variant.get();
            return selected != null ? List.of(selected) : List.of();
        };
    }

    private static int selectedIndex(List<MechVariant> variants, int requested) {
        if (variants == null || variants.isEmpty()) return -1;
        return Math.max(0, Math.min(variants.size() - 1, requested));
    }

    private static void drawSocketOverlays(CanvasContext c, MechFittingLayout layout,
                                           SocketId selectedSocket,
                                           SceneProjection projection) {
        float radians = (float) Math.toRadians(GANTRY_FACING_DEGREES);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        for (SocketDef socket : layout.sockets()) {
            SocketDropTarget target = socketDropTarget(socket, projection.actorX(),
                    projection.actorY(), projection.hullX(), projection.hullY(), cos, sin);
            boolean occupied = layout.occupied(socket.id());
            boolean selected = socket.id() == selectedSocket;
            Color base = socketColor(socket.type());
            int fillAlpha = selected ? 92 : occupied ? 38 : 72;
            int strokeAlpha = selected ? 240 : occupied ? 128 : 210;
            c.line(target.anchorX(), target.anchorY(), target.centerX(), target.centerY(),
                    withAlpha(base, selected ? 210 : occupied ? 90 : 165),
                    selected ? 2f : 1f);
            c.fillRect(target.anchorX() - 3f, target.anchorY() - 3f, 6f, 6f,
                    withAlpha(base, selected ? 245 : 180));
            c.fillRect(target.left(), target.top(), target.width(), target.height(),
                    withAlpha(base, fillAlpha));
            c.strokeRect(target.left(), target.top(), target.width(), target.height(),
                    withAlpha(base, strokeAlpha),
                    selected ? 2f : 1f);
            drawCapacityCells(c, target, base, selected, occupied);
        }
    }

    static SocketDropTarget socketDropTarget(SocketDef socket,
                                             float actorX, float actorY,
                                             float hullWidth, float hullHeight) {
        float radians = (float) Math.toRadians(GANTRY_FACING_DEGREES);
        return socketDropTarget(socket, actorX, actorY, hullWidth, hullHeight,
                (float) Math.cos(radians), (float) Math.sin(radians));
    }

    private static SocketDropTarget socketDropTarget(SocketDef socket,
                                                      float actorX, float actorY,
                                                      float hullWidth, float hullHeight,
                                                      float cos, float sin) {
        float anchorLocalX = socket.localRight() * hullWidth;
        float anchorLocalY = socket.localForward() * hullHeight;
        float anchorWorldX = anchorLocalX * cos - anchorLocalY * sin;
        float anchorWorldY = anchorLocalX * sin + anchorLocalY * cos;
        float dockLocalX = socket.dockRight() * hullWidth;
        float dockLocalY = socket.dockForward() * hullHeight;
        float dockWorldX = dockLocalX * cos - dockLocalY * sin;
        float dockWorldY = dockLocalX * sin + dockLocalY * cos;
        float width = Math.max(MIN_DROP_TARGET_WIDTH,
                socket.footprintWidthHull() * hullWidth);
        float height = Math.max(MIN_DROP_TARGET_HEIGHT,
                socket.footprintHeightHull() * hullHeight);
        return new SocketDropTarget(socket.id(), socket.capacity(),
                actorX + anchorWorldX, actorY - anchorWorldY,
                actorX + dockWorldX, actorY - dockWorldY, width, height);
    }

    static List<CapacityCell> capacityCells(SocketDropTarget target) {
        float bandHeight = Math.max(6f, Math.min(10f, target.height() * 0.22f));
        float availableWidth = target.width() - CAPACITY_INSET * 2f
                - CAPACITY_GAP * (target.capacity() - 1);
        float cellWidth = availableWidth / target.capacity();
        float x = target.left() + CAPACITY_INSET;
        float y = target.bottom() - CAPACITY_INSET - bandHeight;
        List<CapacityCell> cells = new ArrayList<>(target.capacity());
        for (int index = 0; index < target.capacity(); index++) {
            cells.add(new CapacityCell(index, x + index * (cellWidth + CAPACITY_GAP),
                    y, cellWidth, bandHeight));
        }
        return List.copyOf(cells);
    }

    private static void drawCapacityCells(CanvasContext c, SocketDropTarget target,
                                          Color base, boolean selected, boolean occupied) {
        int alpha = selected ? 235 : occupied ? 125 : 210;
        for (CapacityCell cell : capacityCells(target)) {
            c.fillRect(cell.x(), cell.y(), cell.width(), cell.height(),
                    withAlpha(base, alpha));
        }
    }

    record SocketDropTarget(SocketId id, int capacity,
                            float anchorX, float anchorY,
                            float centerX, float centerY,
                            float width, float height) {
        float left() { return centerX - width * 0.5f; }
        float top() { return centerY - height * 0.5f; }
        float right() { return centerX + width * 0.5f; }
        float bottom() { return centerY + height * 0.5f; }

        boolean contains(float x, float y) {
            return x >= left() && x <= right() && y >= top() && y <= bottom();
        }
    }

    record CapacityCell(int index, float x, float y, float width, float height) { }

    private static Color socketColor(SocketType type) {
        return switch (type) {
            case CORE -> new Color(0xF0, 0xC9, 0x52);
            case BALLISTIC -> new Color(0xE5, 0x83, 0x45);
            case MISSILE -> new Color(0x6D, 0xD5, 0xF2);
            case AMMO -> new Color(0x9E, 0xBD, 0x6A);
            case UTILITY -> new Color(0xB1, 0x8B, 0xE8);
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void drawTechnician(CanvasContext c, LayeredUnitAssets crew,
                                       float actorX, float canvasY, float shoulderPx,
                                       float facingDegrees, float locomotionPhase,
                                       boolean moving) {
        float surfaceHeight = c.metrics().surfaceHeight();
        int flags = moving ? LayeredAppearance.FLAG_MOVING : 0;
        LayeredUnitComposer.emit(
                (layer, centerX, centerY, spriteWidth, spriteHeight, angle,
                 red, green, blue, alpha) -> c.sprite(layer.sourcePath, layer.sprite,
                        centerX, surfaceHeight - centerY, spriteWidth, spriteHeight,
                        angle, new Color(red, green, blue, alpha)),
                crew, crew.head, null, false, null,
                EquipmentGrade.SERVICE, actorX, surfaceHeight - canvasY, shoulderPx,
                facingDegrees, 0f, locomotionPhase, 1f,
                LayeredAppearance.POSE_IDLE, flags, 1f);
    }

    private static void drawWeld(CanvasContext c, SceneProjection projection, float time) {
        // Deterministic welding flicker; headless snapshots hold time at zero.
        if (((int) (time * 7f)) % 3 != 1) {
            float x = projection.actorX() + projection.cellX() * 1.45f;
            float y = projection.actorY() + projection.cellY() * 0.35f;
            c.line(x, y, x - 14f, y - 8f, WELD, 2f);
            c.line(x, y, x + 17f, y - 3f, WELD, 2f);
            c.line(x, y, x + 9f, y + 13f, WELD, 2f);
            c.fillRect(x - 3f, y - 3f, 6f, 6f, WHITE);
        }
    }

    private static int appearance(MechWeaponComponent component) {
        return component != null ? component.appearanceSelector : LayeredMechAppearance.POD_NONE;
    }

    /** Scene geometry expressed back in the canvas surface's top-left coordinates. */
    private record SceneProjection(float actorX, float actorY,
                                   float cellX, float cellY,
                                   float hullX, float hullY) {

        private static SceneProjection forCanvas(BattleCamera camera, float surfaceHeight,
                                                 MechVariant variant, int gantryIndex) {
            float cell = camera.cellPxSize();
            float hull = UnitRenderService.layeredMechHullWidth(cell, variant.renderScale);
            return new SceneProjection(
                    camera.cellToScreenX(MechLabBattleScene.mechWorldX(gantryIndex)),
                    surfaceHeight - camera.cellToScreenY(MechLabBattleScene.mechWorldY(gantryIndex)),
                    cell, cell, hull, hull);
        }

        private static SceneProjection forLive(BattleCamera camera,
                                               CanvasHostViewport viewport,
                                               MechVariant variant, int gantryIndex) {
            float cell = camera.cellPxSize();
            float hull = UnitRenderService.layeredMechHullWidth(cell, variant.renderScale);
            float scaleX = viewport.scaleX();
            float scaleY = viewport.scaleY();
            return new SceneProjection(
                    camera.cellToScreenX(MechLabBattleScene.mechWorldX(gantryIndex)) / scaleX,
                    (viewport.height()
                            - camera.cellToScreenY(MechLabBattleScene.mechWorldY(gantryIndex))) / scaleY,
                    cell / scaleX, cell / scaleY,
                    hull / scaleX, hull / scaleY);
        }
    }

    private record CanvasSink(CanvasContext context, float surfaceHeight)
            implements LayeredMechComposer.Sink {
        @Override
        public void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                           float width, float height, float angleDegrees, float alpha) {
            context.sprite(sprite.sourcePath, sprite.sprite,
                    centerX, surfaceHeight - centerY, width, height,
                    angleDegrees, new Color(1f, 1f, 1f, alpha));
        }
    }

    /** Asset identity for snapshot rendering without live Starsector sprites. */
    public static LayeredMechAssets headlessAssets() {
        return new LayeredMechAssets(
                token("chassis.png", 208, 208),
                token("chassis-socketed-variant.png", 208, 208),
                token("chassis-hound.png", 208, 208),
                token("chassis-sirocco.png", 208, 208),
                token("foot.png", 44, 38), token("thigh-bone.png", 40, 112),
                token("chaingun-arm.png", 62, 112),
                token("linear-cannon-variant.png", 58, 138),
                token("heavy-cannon.png", 64, 128), token("srm-pod.png", 62, 88),
                token("lrm-pod.png", 76, 96),
                LayeredSpriteCache.headless(
                        "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png",
                        48, 48));
    }

    private static LayeredSpriteCache token(String name, int width, int height) {
        return LayeredSpriteCache.headless(ROOT + name, width, height);
    }
}
