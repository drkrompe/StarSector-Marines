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
        HardwareArt.preload();
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

        int gridColumns = definition != null ? definition.gridColumns()
                : component != null ? component.footprintColumns : 1;
        int gridRows = definition != null ? definition.gridRows()
                : component != null ? component.footprintRows : 1;
        HardpointBricks.Layout layout = HardpointBricks.fit(inset, inset,
                width - inset * 2f, height - inset * 2f, gridColumns, gridRows, gap);

        // A fitted weapon fills its socket; an internal or replenisher fills the socket it sits in.
        boolean filled = component != null || subsystem != null || occupied.getAsBoolean();
        Color tone = component != null ? HardpointBricks.tone(component.hardpointType)
                : definition != null ? HardpointBricks.tone(definition.type())
                : HardpointBricks.INTERNAL;
        if (definition != null) HardpointBricks.drawEmpty(context, layout, tone);
        if (filled) {
            HardpointBricks.drawFilled(context, layout, 0, 0, layout.columns(), layout.rows(), tone);
        }
        drawEquipment(context, component, subsystem, layout);
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
                               MissileReplenisherComponent subsystem, HardpointBricks.Layout layout) {
        float areaWidth = layout.width();
        float areaHeight = layout.height();
        if (component != null && HardwareArt.draw(context, component, layout.left(), layout.top(),
                areaWidth, areaHeight, 255)) {
            return;
        }
        if (component != null && assets.get() != null) {
            LayeredSpriteCache sprite = spriteFor(assets.get(), component);
            if (sprite != null) {
                float scale = Math.min(areaWidth * 0.84f / sprite.pxWidth,
                        areaHeight * 0.84f / sprite.pxHeight);
                context.sprite(sprite.sourcePath, sprite.sprite,
                        layout.centerX(), layout.centerY(),
                        sprite.pxWidth * scale, sprite.pxHeight * scale,
                        0f, new Color(1f, 1f, 1f, 0.95f));
                return;
            }
        }
        if (subsystem != null) {
            MechReplenisherArt.draw(context, subsystem, layout.centerX(), layout.centerY(),
                    Math.min(areaWidth, areaHeight) * .84f);
        }
    }

    static LayeredSpriteCache spriteFor(LayeredMechAssets assets,
                                         MechWeaponComponent component) {
        return switch (component) {
            case DUAL_CHAINGUNS, NOSE_CHAINGUN -> assets.chaingunArm;
            case DUAL_LINEAR_CANNONS -> assets.linearCannon;
            case SINGLE_HEAVY_CANNON -> assets.heavyCannon;
            // The rack art follows tube count, independently of missile range.
            case SRM_5, LRM_5, SRM_5_FRAG, LRM_5_FRAG -> assets.srmPod;
            case SRM_15, LRM_15, SRM_15_FRAG, LRM_15_FRAG -> assets.lrmPod;
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
