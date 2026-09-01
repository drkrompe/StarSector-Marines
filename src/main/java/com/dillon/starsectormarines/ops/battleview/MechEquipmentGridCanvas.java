package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Canonical 2x3 fitting-grid projection shared by catalog cards and socket rows. */
public final class MechEquipmentGridCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x07, 0x11, 0x19);
    private static final Color BLOCKED = new Color(0x1A, 0x21, 0x29);
    private static final Color BLOCKED_LINE = new Color(0x42, 0x4B, 0x54);

    private final Supplier<SocketDef> socket;
    private final Supplier<MechWeaponComponent> weapon;
    private final Supplier<MissileReplenisherComponent> replenisher;
    private final BooleanSupplier occupied;
    private final Supplier<LayeredMechAssets> assets;

    public MechEquipmentGridCanvas(Supplier<SocketDef> socket,
                                   Supplier<MechWeaponComponent> weapon,
                                   Supplier<MissileReplenisherComponent> replenisher,
                                   BooleanSupplier occupied,
                                   Supplier<LayeredMechAssets> assets) {
        this.socket = socket;
        this.weapon = weapon;
        this.replenisher = replenisher;
        this.occupied = occupied;
        this.assets = assets;
    }

    public static MechEquipmentGridCanvas catalog(MechWeaponComponent weapon,
                                                   MissileReplenisherComponent replenisher,
                                                   Supplier<LayeredMechAssets> assets) {
        return new MechEquipmentGridCanvas(() -> null, () -> weapon, () -> replenisher,
                () -> true, assets);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float inset = Math.max(4f, Math.min(width, height) * 0.07f);
        float gap = Math.max(2f, Math.min(width, height) * 0.025f);
        float cellWidth = (width - inset * 2f - gap * 2f)
                / MechFittingLayout.MAX_GRID_COLUMNS;
        float cellHeight = (height - inset * 2f - gap)
                / MechFittingLayout.MAX_GRID_ROWS;
        context.fillRect(0f, 0f, width, height, BACKGROUND);

        SocketDef definition = socket.get();
        boolean catalogPreview = definition == null;
        MechWeaponComponent component = weapon.get();
        MissileReplenisherComponent subsystem = replenisher.get();
        int activeColumns = definition != null
                ? definition.gridColumns() : MechFittingLayout.MAX_GRID_COLUMNS;
        int activeRows = definition != null
                ? definition.gridRows() : MechFittingLayout.MAX_GRID_ROWS;
        int itemColumns = component != null ? component.footprintColumns
                : subsystem != null || occupied.getAsBoolean() ? 1 : 0;
        int itemRows = component != null ? component.footprintRows
                : subsystem != null || occupied.getAsBoolean() ? 1 : 0;
        float itemStartColumn = catalogPreview
                ? Math.max(0f, (activeColumns - itemColumns) * 0.5f)
                : Math.max(0, (activeColumns - itemColumns) / 2);
        float itemStartRow = catalogPreview
                ? Math.max(0f, (activeRows - itemRows) * 0.5f)
                : Math.max(0, (activeRows - itemRows) / 2);
        Color accent = color(definition != null ? definition.type()
                : component != null ? socketType(component) : SocketType.UTILITY);

        if (catalogPreview) {
            float gridLeft = inset + itemStartColumn * (cellWidth + gap);
            float gridTop = inset + itemStartRow * (cellHeight + gap);
            for (int row = 0; row < itemRows; row++) {
                for (int column = 0; column < itemColumns; column++) {
                    float x = gridLeft + column * (cellWidth + gap);
                    float y = gridTop + row * (cellHeight + gap);
                    context.fillRect(x, y, cellWidth, cellHeight,
                            withAlpha(accent, 152));
                }
            }
        } else {
            for (int row = 0; row < MechFittingLayout.MAX_GRID_ROWS; row++) {
                for (int column = 0; column < MechFittingLayout.MAX_GRID_COLUMNS; column++) {
                    float x = inset + column * (cellWidth + gap);
                    float y = inset + row * (cellHeight + gap);
                    boolean active = column < activeColumns && row < activeRows;
                    boolean filled = active && column >= itemStartColumn
                            && column < itemStartColumn + itemColumns
                            && row >= itemStartRow && row < itemStartRow + itemRows;
                    context.fillRect(x, y, cellWidth, cellHeight,
                            !active ? BLOCKED : withAlpha(accent, filled ? 126 : 28));
                    if (!active) {
                        context.line(x + 3f, y + 3f, x + cellWidth - 3f,
                                y + cellHeight - 3f, BLOCKED_LINE, 1f);
                        context.line(x + cellWidth - 3f, y + 3f, x + 3f,
                                y + cellHeight - 3f, BLOCKED_LINE, 1f);
                    }
                }
            }
        }

        float itemLeft = inset + itemStartColumn * (cellWidth + gap);
        float itemTop = inset + itemStartRow * (cellHeight + gap);
        float itemWidth = itemColumns * cellWidth + Math.max(0, itemColumns - 1) * gap;
        float itemHeight = itemRows * cellHeight + Math.max(0, itemRows - 1) * gap;
        drawEquipment(context, component, subsystem,
                itemLeft, itemTop, itemWidth, itemHeight);

        int strokeColumns = catalogPreview
                ? itemColumns : MechFittingLayout.MAX_GRID_COLUMNS;
        int strokeRows = catalogPreview ? itemRows : MechFittingLayout.MAX_GRID_ROWS;
        float strokeLeft = catalogPreview ? itemLeft : inset;
        float strokeTop = catalogPreview ? itemTop : inset;
        for (int row = 0; row < strokeRows; row++) {
            for (int column = 0; column < strokeColumns; column++) {
                float x = strokeLeft + column * (cellWidth + gap);
                float y = strokeTop + row * (cellHeight + gap);
                boolean active = catalogPreview
                        || column < activeColumns && row < activeRows;
                context.strokeRect(x, y, cellWidth, cellHeight,
                        active ? withAlpha(accent, 220) : BLOCKED_LINE,
                        active ? 1.5f : 1f);
            }
        }
    }

    private void drawEquipment(CanvasContext context, MechWeaponComponent component,
                               MissileReplenisherComponent subsystem,
                               float left, float top,
                               float areaWidth, float areaHeight) {
        if (areaWidth <= 0f || areaHeight <= 0f) return;
        LayeredMechAssets sprites = assets.get();
        if (drawWeaponPreview(context, component, sprites,
                left, top, areaWidth, areaHeight, 0.95f)) return;
        if (subsystem != null) {
            Color utility = color(SocketType.UTILITY);
            float pad = Math.min(areaWidth, areaHeight) * 0.18f;
            context.fillRect(left + pad, top + pad, areaWidth - pad * 2f,
                    areaHeight - pad * 2f, withAlpha(utility, 185));
            context.line(left + areaWidth * 0.5f, top + pad,
                    left + areaWidth * 0.5f, top + areaHeight - pad,
                    new Color(0xD6, 0xF0, 0xC5), 2f);
        }
    }

    static float previewAngleDegrees(LayeredSpriteCache sprite) {
        return sprite != null && sprite.pxHeight > sprite.pxWidth ? 90f : 0f;
    }

    /** Draws the inventory weapon token into one fitting footprint. */
    static boolean drawWeaponPreview(CanvasContext context,
                                     MechWeaponComponent component,
                                     LayeredMechAssets assets,
                                     float left, float top,
                                     float areaWidth, float areaHeight,
                                     float alpha) {
        if (context == null || component == null || assets == null
                || areaWidth <= 0f || areaHeight <= 0f) return false;
        LayeredSpriteCache sprite = spriteFor(assets, component);
        if (sprite == null) return false;
        float angle = previewAngleDegrees(sprite);
        float visualWidth = angle == 0f ? sprite.pxWidth : sprite.pxHeight;
        float visualHeight = angle == 0f ? sprite.pxHeight : sprite.pxWidth;
        float scale = Math.min(areaWidth * 0.88f / visualWidth,
                areaHeight * 0.88f / visualHeight);
        context.sprite(sprite.sourcePath, sprite.sprite,
                left + areaWidth * 0.5f, top + areaHeight * 0.5f,
                sprite.pxWidth * scale, sprite.pxHeight * scale,
                angle, new Color(1f, 1f, 1f, alpha));
        return true;
    }

    private static SocketType socketType(MechWeaponComponent component) {
        return switch (component.hardpointType) {
            case BALLISTIC -> SocketType.BALLISTIC;
            case ENERGY -> SocketType.OMNI;
            case MISSILE -> SocketType.MISSILE;
        };
    }

    static LayeredSpriteCache spriteFor(LayeredMechAssets assets,
                                        MechWeaponComponent component) {
        return switch (component) {
            case DUAL_CHAINGUNS, NOSE_CHAINGUN -> assets.chaingunArm;
            case DUAL_LINEAR_CANNONS -> assets.linearCannon;
            case SINGLE_HEAVY_CANNON -> assets.heavyCannon;
            case SRM_5, SRM_15 -> assets.srmPod;
            case LRM_5, LRM_15 -> assets.lrmPod;
            case SHOULDER_LASER_CANNON -> assets.shoulderLaser;
            case DUAL_PULSE_LASERS -> assets.pulseLaserArm;
            case DUAL_BASTION_AUTOCANNONS -> assets.bastionAutocannon;
            case DEMOLITION_CANNON -> assets.demolitionCannon;
            case THERMAL_LANCE -> assets.thermalLance;
            case DUAL_MUSTER_AUTOGUNS, NOSE_MUSTER_AUTOGUN -> assets.musterAutogun;
            case QUARRY_BREAKER_CANNON -> assets.quarryBreakerCannon;
            case PIONEER_ROCKET_CRADLE -> assets.pioneerRocketCradle;
        };
    }

    private static Color color(SocketType type) {
        return switch (type) {
            case CORE -> new Color(0xF0, 0xC9, 0x52);
            case BALLISTIC -> new Color(0xE5, 0x83, 0x45);
            case MISSILE -> new Color(0x6D, 0xD5, 0xF2);
            case OMNI -> new Color(0xB5, 0x87, 0xF4);
            case AMMO -> new Color(0x9E, 0xBD, 0x6A);
            case UTILITY -> new Color(0x8F, 0xC7, 0x85);
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
