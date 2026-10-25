package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Canonical fitting-grid projection shared by catalog cards and socket rows. */
public final class MechEquipmentGridCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x10, 0x13, 0x11);

    private final Supplier<SocketDef> socket;
    private final Supplier<MechWeaponComponent> weapon;
    private final Supplier<List<MechCompartment.Placement>> compartment;
    private final Supplier<MechInternal> internal;
    private final BooleanSupplier occupied;
    private final Supplier<LayeredMechAssets> assets;

    public MechEquipmentGridCanvas(Supplier<SocketDef> socket,
                                   Supplier<MechWeaponComponent> weapon,
                                   Supplier<List<MechCompartment.Placement>> compartment,
                                   Supplier<MechInternal> internal,
                                   BooleanSupplier occupied,
                                   Supplier<LayeredMechAssets> assets) {
        this.socket = socket;
        this.weapon = weapon;
        this.compartment = compartment;
        this.internal = internal;
        this.occupied = occupied;
        this.assets = assets;
        HardwareArt.preload();
    }

    public static MechEquipmentGridCanvas catalog(MechWeaponComponent weapon,
                                                   MechInternal internal,
                                                   Supplier<LayeredMechAssets> assets) {
        return new MechEquipmentGridCanvas(() -> null, () -> weapon, List::of,
                () -> internal, () -> true, assets);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        context.fillRect(0f, 0f, width, height, BACKGROUND);

        SocketDef definition = socket.get();
        MechWeaponComponent component = weapon.get();
        MechInternal part = component == null ? internal.get() : null;

        int gridColumns = definition != null ? definition.gridColumns()
                : component != null ? component.footprintColumns
                : part != null ? part.footprintColumns : 2;
        int gridRows = definition != null ? definition.gridRows()
                : component != null ? component.footprintRows
                : part != null ? part.footprintRows : 1;
        // The board's own brick: a part or socket is the same size here as on the doll.
        HardpointBricks.Layout layout = HardpointBricks.centred(0f, 0f, width, height,
                gridColumns, gridRows, context.metrics());

        // A fitted weapon fills its socket; an internal fills its own footprint.
        Color tone = component != null ? HardpointBricks.tone(component.hardpointType)
                : part != null ? HardpointBricks.tone(part.role)
                : definition != null ? HardpointBricks.tone(definition.type())
                : HardpointBricks.UTILITY;
        if (definition != null) HardpointBricks.drawEmpty(context, layout, tone);
        List<MechCompartment.Placement> packed = definition != null && component == null
                ? compartment.get() : null;
        if (packed != null && !packed.isEmpty()) {
            // The compartment draws each part where the packing rule put it.
            for (MechCompartment.Placement placed : packed) {
                HardpointBricks.drawFilled(context, layout, placed.column(), placed.row(),
                        placed.columns(), placed.rows(), HardpointBricks.tone(placed.part().role));
                HardwareArt.draw(context, placed.part(), layout.brickX(placed.column()), layout.brickY(placed.row()),
                        layout.span(placed.columns(), layout.brickWidth()),
                        layout.span(placed.rows(), layout.brickHeight()), 255);
            }
            return;
        }
        if (part != null) {
            // An internal fills its own footprint, which may be smaller than the socket.
            int columns = Math.min(layout.columns(), part.footprintColumns);
            int rows = Math.min(layout.rows(), part.footprintRows);
            HardpointBricks.drawFilled(context, layout, 0, 0, columns, rows, tone);
            HardwareArt.draw(context, part, layout.left(), layout.top(),
                    layout.span(columns, layout.brickWidth()), layout.span(rows, layout.brickHeight()), 255);
            return;
        }
        boolean filled = component != null || occupied.getAsBoolean();
        if (filled) {
            HardpointBricks.drawFilled(context, layout, 0, 0, layout.columns(), layout.rows(), tone);
        }
        drawEquipment(context, component, layout);
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
                               HardpointBricks.Layout layout) {
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
            }
        }
    }

    static LayeredSpriteCache spriteFor(LayeredMechAssets assets,
                                         MechWeaponComponent component) {
        return switch (component) {
            case CHAINGUN, NOSE_CHAINGUN, CHAINGUN_HF, NOSE_CHAINGUN_HF -> assets.chaingunArm;
            case LINEAR_CANNON -> assets.linearCannon;
            case SINGLE_HEAVY_CANNON -> assets.heavyCannon;
            // The rack art follows tube count, independently of missile range.
            case SRM_5, LRM_5, SRM_5_FRAG, LRM_5_FRAG -> assets.srmPod;
            case SRM_15, LRM_15, SRM_15_FRAG, LRM_15_FRAG -> assets.lrmPod;
            case SHOULDER_LASER_CANNON -> assets.shoulderLaser;
            case PULSE_LASER -> assets.pulseLaserArm;
            case BASTION_AUTOCANNON -> assets.bastionAutocannon;
            case DEMOLITION_CANNON -> assets.demolitionCannon;
            case THERMAL_LANCE -> assets.thermalLance;
            case MUSTER_AUTOGUN, NOSE_MUSTER_AUTOGUN -> assets.musterAutogun;
            case QUARRY_BREAKER_CANNON -> assets.quarryBreakerCannon;
            case PIONEER_ROCKET_CRADLE -> assets.pioneerRocketCradle;
        };
    }

}
