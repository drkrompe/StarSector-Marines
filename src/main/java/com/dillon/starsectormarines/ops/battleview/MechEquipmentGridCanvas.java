package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Canonical fitting-grid projection shared by catalog cards and socket rows. */
public final class MechEquipmentGridCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x10, 0x13, 0x11);

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
        context.fillRect(0f, 0f, width, height, BACKGROUND);

        SocketDef definition = socket.get();
        MechWeaponComponent component = weapon.get();
        MissileReplenisherComponent subsystem = replenisher.get();

        GridMetrics metrics = resolveGridMetrics(definition, component, width, height, inset, gap);
        int gridColumns = metrics.columns();
        int gridRows = metrics.rows();
        float cellWidth = metrics.cellWidth();
        float cellHeight = metrics.cellHeight();

        int itemColumns = definition != null && component != null ? gridColumns
                : component != null ? component.footprintColumns
                : subsystem != null || occupied.getAsBoolean() ? 1 : 0;
        int itemRows = definition != null && component != null ? gridRows
                : component != null ? component.footprintRows
                : subsystem != null || occupied.getAsBoolean() ? 1 : 0;
        itemColumns = Math.min(gridColumns, itemColumns);
        itemRows = Math.min(gridRows, itemRows);
        int itemStartColumn = Math.max(0, (gridColumns - itemColumns) / 2);
        int itemStartRow = Math.max(0, (gridRows - itemRows) / 2);

        for (int row = 0; row < gridRows; row++) {
            for (int column = 0; column < gridColumns; column++) {
                float x = inset + column * (cellWidth + gap);
                float y = inset + row * (cellHeight + gap);
                boolean filled = column >= itemStartColumn
                        && column < itemStartColumn + itemColumns
                        && row >= itemStartRow && row < itemStartRow + itemRows;
                context.fillRect(x, y, cellWidth, cellHeight,
                        filled ? new Color(0x34, 0x39, 0x32) : new Color(0x1a, 0x1e, 0x1b));
            }
        }

        drawEquipment(context, component, subsystem,
                itemColumns, itemRows, itemStartColumn, itemStartRow,
                inset, gap, cellWidth, cellHeight);

        for (int row = 0; row < gridRows; row++) {
            for (int column = 0; column < gridColumns; column++) {
                float x = inset + column * (cellWidth + gap);
                float y = inset + row * (cellHeight + gap);
                context.strokeRect(x, y, cellWidth, cellHeight,
                        new Color(0x78, 0x7e, 0x71), 1.5f);
            }
        }
    }

    record GridMetrics(int columns, int rows, float cellWidth, float cellHeight) {}

    static GridMetrics resolveGridMetrics(SocketDef definition, MechWeaponComponent component,
                                          float width, float height, float inset, float gap) {
        int gridColumns = definition != null
                ? definition.gridColumns()
                : component != null ? component.footprintColumns : 1;
        int gridRows = definition != null
                ? definition.gridRows()
                : component != null ? component.footprintRows : 1;
        gridColumns = Math.max(1, gridColumns);
        gridRows = Math.max(1, gridRows);
        float cellWidth = (width - inset * 2f - gap * (gridColumns - 1)) / gridColumns;
        float cellHeight = (height - inset * 2f - gap * (gridRows - 1)) / gridRows;
        return new GridMetrics(gridColumns, gridRows, cellWidth, cellHeight);
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
            MechReplenisherArt.draw(context, subsystem,
                    left + areaWidth * .5f, top + areaHeight * .5f,
                    Math.min(areaWidth, areaHeight) * .88f);
        }
    }

    static LayeredSpriteCache spriteFor(LayeredMechAssets assets,
                                         MechWeaponComponent component) {
        return switch (component) {
            case DUAL_CHAINGUNS, NOSE_CHAINGUN -> assets.chaingunArm;
            case DUAL_LINEAR_CANNONS -> assets.linearCannon;
            case SINGLE_HEAVY_CANNON -> assets.heavyCannon;
            // The rack art follows tube count, independently of missile range.
            case SRM_5, LRM_5 -> assets.srmPod;
            case SRM_15, LRM_15 -> assets.lrmPod;
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

}
