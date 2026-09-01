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

/** Canonical 3x2 fitting-grid projection shared by catalog cards and socket rows. */
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
        int itemStartColumn = Math.max(0, (activeColumns - itemColumns) / 2);
        int itemStartRow = Math.max(0, (activeRows - itemRows) / 2);
        Color accent = color(definition != null ? definition.type()
                : component != null ? socketType(component) : SocketType.UTILITY);

        for (int row = 0; row < MechFittingLayout.MAX_GRID_ROWS; row++) {
            for (int column = 0; column < MechFittingLayout.MAX_GRID_COLUMNS; column++) {
                float x = inset + column * (cellWidth + gap);
                float y = inset + row * (cellHeight + gap);
                boolean active = column < activeColumns && row < activeRows;
                boolean filled = active && column >= itemStartColumn
                        && column < itemStartColumn + itemColumns
                        && row >= itemStartRow && row < itemStartRow + itemRows;
                context.fillRect(x, y, cellWidth, cellHeight,
                        !active ? BLOCKED : withAlpha(accent, filled ? 116 : 28));
                if (!active) {
                    context.line(x + 3f, y + 3f, x + cellWidth - 3f,
                            y + cellHeight - 3f, BLOCKED_LINE, 1f);
                    context.line(x + cellWidth - 3f, y + 3f, x + 3f,
                            y + cellHeight - 3f, BLOCKED_LINE, 1f);
                }
            }
        }

        drawEquipment(context, component, subsystem,
                itemColumns, itemRows, itemStartColumn, itemStartRow,
                inset, gap, cellWidth, cellHeight);

        for (int row = 0; row < MechFittingLayout.MAX_GRID_ROWS; row++) {
            for (int column = 0; column < MechFittingLayout.MAX_GRID_COLUMNS; column++) {
                float x = inset + column * (cellWidth + gap);
                float y = inset + row * (cellHeight + gap);
                boolean active = column < activeColumns && row < activeRows;
                context.strokeRect(x, y, cellWidth, cellHeight,
                        active ? withAlpha(accent, 205) : BLOCKED_LINE, active ? 1.5f : 1f);
            }
        }
    }

    private void drawEquipment(CanvasContext context, MechWeaponComponent component,
                               MissileReplenisherComponent subsystem,
                               int itemColumns, int itemRows,
                               int startColumn, int startRow,
                               float inset, float gap, float cellWidth, float cellHeight) {
        if (itemColumns <= 0 || itemRows <= 0) return;
        float left = inset + startColumn * (cellWidth + gap);
        float top = inset + startRow * (cellHeight + gap);
        float areaWidth = itemColumns * cellWidth + (itemColumns - 1) * gap;
        float areaHeight = itemRows * cellHeight + (itemRows - 1) * gap;
        if (component != null && assets.get() != null) {
            LayeredSpriteCache sprite = spriteFor(assets.get(), component);
            if (sprite != null) {
                float scale = Math.min(areaWidth * 0.88f / sprite.pxWidth,
                        areaHeight * 0.88f / sprite.pxHeight);
                context.sprite(sprite.sourcePath, sprite.sprite,
                        left + areaWidth * 0.5f, top + areaHeight * 0.5f,
                        sprite.pxWidth * scale, sprite.pxHeight * scale,
                        0f, new Color(1f, 1f, 1f, 0.95f));
                return;
            }
        }
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

    private static SocketType socketType(MechWeaponComponent component) {
        return switch (component.hardpointType) {
            case BALLISTIC -> SocketType.BALLISTIC;
            case ENERGY -> SocketType.OMNI;
            case MISSILE -> SocketType.MISSILE;
        };
    }

    private static LayeredSpriteCache spriteFor(LayeredMechAssets assets,
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
