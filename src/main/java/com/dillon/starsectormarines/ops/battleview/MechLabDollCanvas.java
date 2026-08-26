package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskPose;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
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
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Canonically composed lance inside a top-down battle-tileset fabrication garage. */
public final class MechLabDollCanvas implements CanvasProducer {

    private static final String ROOT = "graphics/battle/mech-modular-topdown/";
    static final String WELDING_TORCH_PATH =
            "graphics/battle/mech-lab/welding-torch.png";
    static final String WELDING_SPARKS_PATH =
            "graphics/battle/mech-lab/welding-sparks-sheet.png";
    private static final int URBAN_COLUMNS = 10;
    private static final int URBAN_ROWS = 10;
    private static final float MIN_DROP_TARGET_WIDTH = 128f;
    private static final float MIN_DROP_TARGET_HEIGHT = 76f;
    private static final float CAPACITY_INSET = 4f;
    private static final float CAPACITY_GAP = 2f;
    private static final Color BACKGROUND = new Color(0x06, 0x0A, 0x10);
    private static final Color WHITE = Color.WHITE;

    private final Supplier<List<MechVariant>> variants;
    private final IntSupplier selectedGantry;
    private final Supplier<SocketId> selectedSocket;
    private final Supplier<LayeredMechAssets> assets;
    private final Supplier<LayeredUnitAssets> technicianAssets;
    private final Supplier<SpriteAPI> tileSheet;
    private final Supplier<SpriteAPI> weldingTorch;
    private final Supplier<SpriteAPI> weldingSparks;
    private final Supplier<MechLabCameraController.CameraPose> cameraPose;
    private final BooleanSupplier fittingOverlaysVisible;
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
                technicianAssets, tileSheet, roadSheet,
                () -> null, () -> null,
                () -> MechLabCameraController.fittingPose(0), () -> true,
                battleScene, elapsedSeconds);
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
        this(variants, selectedGantry, selectedSocket, assets, technicianAssets,
                tileSheet, roadSheet, () -> null, () -> null,
                () -> MechLabCameraController.fittingPose(
                        selectedIndex(selectedGantry.getAsInt())), () -> true,
                battleScene, elapsedSeconds);
    }

    public MechLabDollCanvas(Supplier<List<MechVariant>> variants,
                             IntSupplier selectedGantry,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet,
                             Supplier<SpriteAPI> roadSheet,
                             Supplier<SpriteAPI> weldingTorch,
                             Supplier<SpriteAPI> weldingSparks,
                             Supplier<MechLabCameraController.CameraPose> cameraPose,
                             BooleanSupplier fittingOverlaysVisible,
                             MechLabBattleScene battleScene,
                             DoubleSupplier elapsedSeconds) {
        if (variants == null || selectedGantry == null || selectedSocket == null
                || assets == null || technicianAssets == null
                || tileSheet == null || roadSheet == null
                || weldingTorch == null || weldingSparks == null
                || cameraPose == null || fittingOverlaysVisible == null
                || elapsedSeconds == null) {
            throw new IllegalArgumentException(
                    "variants, gantry, socket, mech/technician/tile assets, and elapsed time are required");
        }
        this.variants = variants;
        this.selectedGantry = selectedGantry;
        this.selectedSocket = selectedSocket;
        this.assets = assets;
        this.technicianAssets = technicianAssets;
        this.tileSheet = tileSheet;
        this.weldingTorch = weldingTorch;
        this.weldingSparks = weldingSparks;
        this.cameraPose = cameraPose;
        this.fittingOverlaysVisible = fittingOverlaysVisible;
        this.battleScene = battleScene;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float time = (float) elapsedSeconds.getAsDouble();
        List<MechVariant> lance = variants.get();
        int gantryIndex = selectedIndex(selectedGantry.getAsInt());
        MechLabCameraController.CameraPose pose = cameraPose.get();
        if (pose == null) return;
        MechVariant selected = gantryIndex < lance.size() ? lance.get(gantryIndex) : null;
        LayeredMechAssets sprites = assets.get();
        if (sprites == null) return;

        CanvasHostViewport[] liveViewport = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = battleScene != null
                ? battleScene.backdropPass(lance, pose, time) : null;
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
                    viewport.width(), viewport.height(), pose);
            projection = selected != null
                    ? SceneProjection.forLive(sceneCamera, viewport, selected, gantryIndex) : null;
        } else {
            sceneCamera = MechLabBattleScene.cameraForSurface(width, height, pose);
            projection = selected != null
                    ? SceneProjection.forCanvas(sceneCamera, height, selected, gantryIndex) : null;
            drawGarage(context, sceneCamera, height, tileSheet.get());
        }

        if (liveScene) {
            context.hostPass(battleScene.actorPass(lance, pose, time));
        } else {
            drawLance(context, sceneCamera, height, sprites, lance);
            drawTechnicians(context, sceneCamera, height,
                    projection != null ? projection.cellX() : sceneCamera.cellPxSize(),
                    time, technicianAssets.get());
        }
        if (selected != null && fittingOverlaysVisible.getAsBoolean()) {
            drawSocketOverlays(context, MechFittingLayout.forVariant(selected),
                    selectedSocket.get(), projection);
        }
        if (projection != null) {
            drawTechnicianFx(context, projection, gantryIndex, time,
                    weldingTorch.get(), weldingSparks.get());
        }
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
                                        float cell, float time, LayeredUnitAssets crew) {
        if (crew == null) return;
        float shoulder = UnitRenderService.layeredInfantryShoulderWidth(
                cell, UnitType.ENGINEER.renderScale);
        for (int index = 0; index < MechLabSceneLayout.TECHNICIAN_JOBS.size(); index++) {
            AmbientTaskPose pose = AmbientTaskService.sample(
                    MechLabSceneLayout.TECHNICIAN_JOBS.get(index), time);
            drawTechnician(c, crew,
                    camera.cellToScreenX(pose.worldX()),
                    height - camera.cellToScreenY(pose.worldY()),
                    shoulder, pose.facingDegrees(), pose.locomotionPhase(), pose.moving());
        }
    }

    private static void drawLance(CanvasContext context, BattleCamera camera, float height,
                                  LayeredMechAssets sprites, List<MechVariant> variants) {
        int count = Math.min(variants.size(), MechLabSceneLayout.GANTRIES.size());
        for (int index = 0; index < count; index++) {
            MechVariant variant = variants.get(index);
            MechFittingLayout layout = MechFittingLayout.forVariant(variant);
            MechLabSceneLayout.Gantry gantry = MechLabSceneLayout.GANTRIES.get(index);
            float hull = UnitRenderService.layeredMechHullWidth(
                    camera.cellPxSize(), variant.renderScale);
            LayeredMechComposer.emit(new CanvasSink(context, height), sprites,
                    camera.cellToScreenX(gantry.cellX() + 0.5f),
                    camera.cellToScreenY(gantry.cellY() + 0.5f), hull,
                    layout.doll().facingDegrees(), layout.doll().facingDegrees(),
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

    private static int selectedIndex(int requested) {
        return Math.max(0, Math.min(CampaignMechSquad.CAPACITY - 1, requested));
    }

    private static void drawSocketOverlays(CanvasContext c, MechFittingLayout layout,
                                           SocketId selectedSocket,
                                           SceneProjection projection) {
        float radians = (float) Math.toRadians(layout.doll().facingDegrees());
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        for (SocketDef socket : layout.sockets()) {
            SocketDropTarget target = socketDropTarget(socket, projection.actorX(),
                    projection.actorY(), projection.hullX(), projection.hullY(), cos, sin);
            boolean occupied = layout.occupied(socket.id());
            boolean selected = socket.id() == selectedSocket;
            Color base = socketColor(socket.type());
            int fillAlpha = selected ? 138 : occupied ? 72 : 112;
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

    static SocketDropTarget socketDropTarget(DollDef doll, SocketDef socket,
                                             float actorX, float actorY,
                                             float hullWidth, float hullHeight) {
        float radians = (float) Math.toRadians(doll.facingDegrees());
        return socketDropTarget(socket, actorX, actorY, hullWidth, hullHeight,
                (float) Math.cos(radians), (float) Math.sin(radians));
    }

    private static SocketDropTarget socketDropTarget(SocketDef socket,
                                                      float actorX, float actorY,
                                                      float hullWidth, float hullHeight,
                                                      float cos, float sin) {
        float anchorLocalX = socket.anchorRight() * hullWidth;
        float anchorLocalY = socket.anchorForward() * hullHeight;
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

    private static void drawTechnicianFx(CanvasContext c, SceneProjection projection,
                                         int selectedGantry, float time,
                                         SpriteAPI torch, SpriteAPI sparks) {
        float originX = MechLabBattleScene.mechWorldX(selectedGantry);
        float originY = MechLabBattleScene.mechWorldY(selectedGantry);
        for (int index = 0; index < MechLabSceneLayout.TECHNICIAN_JOBS.size(); index++) {
            AmbientTaskPose pose = AmbientTaskService.sample(
                    MechLabSceneLayout.TECHNICIAN_JOBS.get(index), time);
            if (pose.activity() != AmbientActivity.WORKING) {
                continue;
            }
            float technicianX = projection.actorX()
                    + (pose.worldX() - originX) * projection.cellX();
            float technicianY = projection.actorY()
                    - (pose.worldY() - originY) * projection.cellY();
            float focusX = projection.actorX()
                    + (pose.focusX() - originX) * projection.cellX();
            float focusY = projection.actorY()
                    - (pose.focusY() - originY) * projection.cellY();
            float torchX = lerp(technicianX, focusX, 0.48f);
            float torchY = lerp(technicianY, focusY, 0.48f);
            c.sprite(WELDING_TORCH_PATH, torch,
                    torchX, torchY,
                    projection.cellX() * 0.42f, projection.cellY() * 0.72f,
                    pose.facingDegrees(), WHITE);
            int frame = Math.floorMod(
                    (int) Math.floor(time * 12f + index * 1.7f) + 2, 8);
            c.sprite(WELDING_SPARKS_PATH, sparks,
                    focusX, focusY,
                    projection.cellX() * 1.08f, projection.cellY() * 1.08f,
                    0f, WHITE, CanvasSpriteRegion.frame(4, 2, frame), CanvasBlend.ADDITIVE);
        }
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
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
