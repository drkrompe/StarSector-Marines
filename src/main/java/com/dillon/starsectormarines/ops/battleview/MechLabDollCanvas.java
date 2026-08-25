package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Canonically composed mech inside a top-down battle-tileset fabrication bay. */
public final class MechLabDollCanvas implements CanvasProducer {

    private static final String ROOT = "graphics/battle/mech-modular-topdown/";
    private static final int TILESET_COLUMNS = 10;
    private static final int TILESET_ROWS = 10;
    private static final float GANTRY_FACING_DEGREES = 180f;
    private static final Color BACKGROUND = new Color(0x06, 0x0A, 0x10);
    private static final Color STRUCTURE = new Color(0x25, 0x43, 0x56);
    private static final Color ACCENT = new Color(0x76, 0xB9, 0xD4);
    private static final Color WELD = new Color(0xA5, 0xE8, 0xFF);
    private static final Color WHITE = Color.WHITE;

    private final Supplier<MechVariant> variant;
    private final Supplier<LayeredMechAssets> assets;
    private final Supplier<LayeredUnitAssets> technicianAssets;
    private final Supplier<SpriteAPI> tileSheet;
    private final DoubleSupplier elapsedSeconds;

    public MechLabDollCanvas(Supplier<MechVariant> variant,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet) {
        this(variant, assets, technicianAssets, tileSheet, () -> 0d);
    }

    public MechLabDollCanvas(Supplier<MechVariant> variant,
                             Supplier<LayeredMechAssets> assets,
                             Supplier<LayeredUnitAssets> technicianAssets,
                             Supplier<SpriteAPI> tileSheet,
                             DoubleSupplier elapsedSeconds) {
        if (variant == null || assets == null || technicianAssets == null
                || tileSheet == null || elapsedSeconds == null) {
            throw new IllegalArgumentException(
                    "variant, mech/technician/tile assets, and elapsed time are required");
        }
        this.variant = variant;
        this.assets = assets;
        this.technicianAssets = technicianAssets;
        this.tileSheet = tileSheet;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float time = (float) elapsedSeconds.getAsDouble();
        drawGarage(context, width, height, tileSheet.get());

        MechVariant selected = variant.get();
        LayeredMechAssets sprites = assets.get();
        if (selected == null || sprites == null) return;

        float hull = Math.min(width * 0.42f, height * 0.50f);
        float actorX = width * 0.5f;
        float actorCanvasY = height * 0.50f;
        float actorWorldY = height - actorCanvasY;
        LayeredMechComposer.emit(new CanvasSink(context, height), sprites,
                actorX, actorWorldY, hull,
                GANTRY_FACING_DEGREES, GANTRY_FACING_DEGREES,
                0f, 0f, 0f, 0f, 0,
                selected.chassisAppearance,
                selected.arms.appearanceSelector,
                appearance(selected.leftShoulder),
                appearance(selected.rightShoulder), 1f);

        drawTechnicians(context, width, height, time, technicianAssets.get());
        drawWeld(context, width, height, time);
    }

    private static void drawGarage(CanvasContext c, float width, float height,
                                   SpriteAPI liveSheet) {
        c.fillRect(0f, 0f, width, height, BACKGROUND);
        float cell = Math.max(32f, Math.min(48f, height / 10f));
        int columns = Math.max(1, (int) Math.ceil(width / cell));
        int rows = Math.max(1, (int) Math.ceil(height / cell));
        float originX = (width - columns * cell) * 0.5f;
        float originY = (height - rows * cell) * 0.5f;

        // The bay is a literal battle-map room: floor centers, wall autotile edges,
        // industrial hazard/grate cells, and prop cutouts all come from urban-tileset.
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int tileColumn = 1;
                int tileRow = 1;
                if (row == 0) { tileColumn = 4; tileRow = 0; }
                if (row == rows - 1) { tileColumn = 4; tileRow = 2; }
                if (column == 0) { tileColumn = 3; tileRow = 1; }
                if (column == columns - 1) { tileColumn = 5; tileRow = 1; }
                if (row == 0 && column == 0) { tileColumn = 3; tileRow = 0; }
                if (row == 0 && column == columns - 1) { tileColumn = 5; tileRow = 0; }
                if (row == rows - 1 && column == 0) { tileColumn = 3; tileRow = 2; }
                if (row == rows - 1 && column == columns - 1) { tileColumn = 5; tileRow = 2; }
                drawTile(c, liveSheet, tileColumn, tileRow,
                        originX + (column + 0.5f) * cell,
                        originY + (row + 0.5f) * cell, cell);
            }
        }

        int padLeft = Math.max(2, columns / 2 - 3);
        int padRight = Math.min(columns - 3, columns / 2 + 3);
        int padTop = Math.max(2, rows / 2 - 3);
        int padBottom = Math.min(rows - 3, rows / 2 + 3);
        for (int row = padTop; row <= padBottom; row++) {
            for (int column = padLeft; column <= padRight; column++) {
                boolean perimeter = row == padTop || row == padBottom
                        || column == padLeft || column == padRight;
                int tileColumn = perimeter ? 1 : ((row + column) & 1) == 0 ? 0 : 2;
                int tileRow = 3;
                drawTile(c, liveSheet, tileColumn, tileRow,
                        originX + (column + 0.5f) * cell,
                        originY + (row + 0.5f) * cell, cell);
            }
        }

        // Battle props become top-down fabrication stations around the active pad.
        drawTile(c, liveSheet, 8, 2, width * 0.35f, cell * 0.70f, cell);
        drawTile(c, liveSheet, 8, 2, width * 0.65f, cell * 0.70f, cell);
        drawTile(c, liveSheet, 5, 3, cell * 1.35f, height * 0.36f, cell);
        drawTile(c, liveSheet, 6, 3, cell * 1.35f, height * 0.50f, cell);
        drawTile(c, liveSheet, 7, 3, cell * 1.35f, height * 0.64f, cell);
        drawTile(c, liveSheet, 9, 2, width - cell * 1.35f, height * 0.38f, cell);
        drawTile(c, liveSheet, 9, 1, width - cell * 1.35f, height * 0.57f, cell);
        drawTile(c, liveSheet, 3, 3, width - cell * 1.35f, height * 0.72f, cell);

        // Flat top-down service rails frame the mech without inventing depth.
        float railInset = Math.max(cell * 2.2f, width * 0.23f);
        c.line(railInset, cell * 1.4f, railInset, height - cell * 1.4f,
                STRUCTURE, 5f);
        c.line(width - railInset, cell * 1.4f, width - railInset,
                height - cell * 1.4f, STRUCTURE, 5f);
        c.line(railInset, cell * 1.4f, width - railInset, cell * 1.4f,
                ACCENT, 2f);
        c.strokeRect(1f, 1f, Math.max(0f, width - 2f),
                Math.max(0f, height - 2f), STRUCTURE, 1f);
    }

    private static void drawTile(CanvasContext c, SpriteAPI liveSheet,
                                 int column, int row, float centerX, float centerY,
                                 float size) {
        c.sprite(TileManifest.SHEET, liveSheet, centerX, centerY, size, size,
                0f, WHITE, CanvasSpriteRegion.frame(TILESET_COLUMNS, TILESET_ROWS,
                        row * TILESET_COLUMNS + column), CanvasBlend.NORMAL);
    }

    private static void drawTechnicians(CanvasContext c, float width, float height,
                                        float time, LayeredUnitAssets crew) {
        if (crew == null) return;
        float walkPhase = (time * 0.15f) % 1f;
        float walkX = width * (0.18f + 0.22f * walkPhase);
        drawTechnician(c, crew, walkX, height * 0.82f, 38f, 90f,
                walkPhase, true);
        drawTechnician(c, crew, width * 0.78f, height * 0.66f, 38f, 250f,
                0f, false);
        drawTechnician(c, crew, width * 0.23f, height * 0.58f, 38f, 70f,
                0f, false);
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
                crew, crew.head, MarineWeapon.FIELD_RIFLE, false, null,
                EquipmentGrade.SERVICE, actorX, surfaceHeight - canvasY, shoulderPx,
                facingDegrees, 0f, locomotionPhase, 1f,
                LayeredAppearance.POSE_IDLE, flags, 1f);
    }

    private static void drawWeld(CanvasContext c, float width, float height, float time) {
        // Deterministic welding flicker; headless snapshots hold time at zero.
        if (((int) (time * 7f)) % 3 != 1) {
            float x = width * 0.69f;
            float y = height * 0.55f;
            c.line(x, y, x - 14f, y - 8f, WELD, 2f);
            c.line(x, y, x + 17f, y - 3f, WELD, 2f);
            c.line(x, y, x + 9f, y + 13f, WELD, 2f);
            c.fillRect(x - 3f, y - 3f, 6f, 6f, WHITE);
        }
    }

    private static int appearance(MechWeaponComponent component) {
        return component != null ? component.appearanceSelector : LayeredMechAppearance.POD_NONE;
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
