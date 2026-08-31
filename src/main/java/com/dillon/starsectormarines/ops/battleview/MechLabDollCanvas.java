package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskPose;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
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
import java.util.EnumSet;
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
    private static final float MIN_DROP_TARGET_WIDTH = 128f;
    private static final float MIN_DROP_TARGET_HEIGHT = 76f;
    private static final float CAPACITY_INSET = 4f;
    private static final float CAPACITY_GAP = 2f;
    private static final Color BACKGROUND = new Color(0x06, 0x0A, 0x10);
    private static final Color WHITE = Color.WHITE;

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.VEHICLES);

    private final Supplier<List<MechVariant>> variants;
    private final IntSupplier selectedGantry;
    private final Supplier<SocketId> selectedSocket;
    private final Supplier<LayeredMechAssets> assets;
    private final Supplier<SpriteAPI> weldingTorch;
    private final Supplier<SpriteAPI> weldingSparks;
    private final BooleanSupplier fittingOverlaysVisible;
    /**
     * The ship the lab is aboard.
     *
     * <p>May supply null before she is built, and then the panel composes over
     * a plain ground rather than over a substitute room. There is no second
     * garage to fall back to: a fallback is a second answer to what the bay
     * looks like, and the one time it was consulted - a headless UI snapshot -
     * it was the only answer anybody ever saw.
     */
    private final Supplier<ShipDeckBattleScene> ship;
    private final Supplier<ShipDeckBattleScene.RoomView> roomView;
    private final Supplier<List<Gantry>> berths;
    private final DoubleSupplier elapsedSeconds;

    public MechLabDollCanvas(Supplier<List<MechVariant>> variants,
                             IntSupplier selectedGantry,
                             Supplier<SocketId> selectedSocket,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<SpriteAPI> weldingTorch,
                             Supplier<SpriteAPI> weldingSparks,
                             BooleanSupplier fittingOverlaysVisible,
                             Supplier<ShipDeckBattleScene> ship,
                             Supplier<ShipDeckBattleScene.RoomView> roomView,
                             Supplier<List<Gantry>> berths,
                             DoubleSupplier elapsedSeconds) {
        if (variants == null || selectedGantry == null || selectedSocket == null
                || assets == null || weldingTorch == null || weldingSparks == null
                || fittingOverlaysVisible == null
                || ship == null || roomView == null || berths == null
                || elapsedSeconds == null) {
            throw new IllegalArgumentException(
                    "variants, gantry, socket, mech assets, a ship and elapsed time are required");
        }
        this.variants = variants;
        this.selectedGantry = selectedGantry;
        this.selectedSocket = selectedSocket;
        this.assets = assets;
        this.weldingTorch = weldingTorch;
        this.weldingSparks = weldingSparks;
        this.fittingOverlaysVisible = fittingOverlaysVisible;
        this.ship = ship;
        this.roomView = roomView;
        this.berths = berths;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float time = (float) elapsedSeconds.getAsDouble();
        List<MechVariant> lance = variants.get();
        int gantryIndex = selectedIndex(selectedGantry.getAsInt());
        MechVariant selected = gantryIndex < lance.size() ? lance.get(gantryIndex) : null;
        if (assets.get() == null) return;

        ShipDeckBattleScene aboard = ship.get();
        ShipDeckBattleScene.RoomView bay = aboard != null ? roomView.get() : null;
        if (bay == null) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }
        CanvasHostViewport[] host = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = aboard.pass(bay, BACKDROP_LAYERS);
        boolean rendered = context.hostPass(new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                host[0] = viewport;
                return backdrop.prepare(viewport, alphaMult);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                host[0] = viewport;
                backdrop.draw(viewport, alphaMult);
            }
        });
        if (!rendered || host[0] == null) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        List<Gantry> standing = berths.get();
        float berthX = berthCellX(standing, gantryIndex);
        float berthY = berthCellY(standing, gantryIndex);
        BattleCamera sceneCamera = aboard.cameraFor(
                bay, 0f, 0f, host[0].width(), host[0].height());
        SceneProjection projection = selected != null
                ? SceneProjection.forLive(sceneCamera, host[0], selected, berthX, berthY)
                : null;

        context.hostPass(aboard.pass(bay, ACTOR_LAYERS));
        if (selected != null && fittingOverlaysVisible.getAsBoolean()) {
            drawSocketOverlays(context, MechFittingLayout.forVariant(selected),
                    selectedSocket.get(), projection);
        }
        if (projection != null) {
            drawTechnicianFx(context, projection, berthX, berthY,
                    workingPoses(aboard), time,
                    weldingTorch.get(), weldingSparks.get());
        }
    }

    /**
     * Where the technicians are actually working.
     *
     * <p>Read off the ship rather than off a script laid over the scene. The
     * welding sparks belong at the point somebody is welding, and on a generated
     * bay that is wherever the crew have got to — a fixed list would light up
     * over empty deck the moment the room was laid out differently.
     */
    private static List<AmbientTaskPose> workingPoses(ShipDeckBattleScene aboard) {
        List<AmbientTaskPose> working = new ArrayList<>();
        AmbientTaskService tasks = aboard.simulation().ambientTasks();
        for (long actor : tasks.assigned()) {
            AmbientTaskPose pose = tasks.pose(actor);
            if (pose != null) working.add(pose);
        }
        return working;
    }

    private static float berthCellX(List<Gantry> berths, int index) {
        return berths.isEmpty() ? 0f
                : berths.get(Math.max(0, Math.min(berths.size() - 1, index))).centerX + 0.5f;
    }

    private static float berthCellY(List<Gantry> berths, int index) {
        return berths.isEmpty() ? 0f
                : berths.get(Math.max(0, Math.min(berths.size() - 1, index))).centerY + 0.5f;
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
            case OMNI -> new Color(0xB5, 0x87, 0xF4);
            case AMMO -> new Color(0x9E, 0xBD, 0x6A);
            case UTILITY -> new Color(0xB1, 0x8B, 0xE8);
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void drawTechnicianFx(CanvasContext c, SceneProjection projection,
                                         float originX, float originY,
                                         List<AmbientTaskPose> poses, float time,
                                         SpriteAPI torch, SpriteAPI sparks) {
        for (int index = 0; index < poses.size(); index++) {
            AmbientTaskPose pose = poses.get(index);
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

        private static SceneProjection forLive(BattleCamera camera,
                                               CanvasHostViewport viewport,
                                               MechVariant variant,
                                               float berthX, float berthY) {
            float cell = camera.cellPxSize();
            float hull = UnitRenderService.layeredMechHullWidth(cell, variant.renderScale);
            float scaleX = viewport.scaleX();
            float scaleY = viewport.scaleY();
            return new SceneProjection(
                    camera.cellToScreenX(berthX) / scaleX,
                    (viewport.height() - camera.cellToScreenY(berthY)) / scaleY,
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
                token("shoulder-laser-cannon.png", 76, 128),
                token("pulse-laser-arm.png", 62, 112),
                LayeredSpriteCache.headless(
                        "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png",
                        48, 48));
    }

    private static LayeredSpriteCache token(String name, int width, int height) {
        return LayeredSpriteCache.headless(ROOT + name, width, height);
    }
}
